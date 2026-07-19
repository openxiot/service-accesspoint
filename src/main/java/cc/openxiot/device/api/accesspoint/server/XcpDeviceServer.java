package cc.openxiot.device.api.accesspoint.server;

import cc.openxiot.device.api.accesspoint.server.configurator.XcpCertConfigurator;
import cc.openxiot.device.api.accesspoint.server.endpoint.console.ConsoleService;
import cc.openxiot.device.api.accesspoint.server.limiter.RateLimiter;
import cc.openxiot.device.api.accesspoint.server.endpoint.XcpDeviceEndpoint;
import cc.openxiot.device.api.accesspoint.server.endpoint.XcpDeviceEndpointManager;
import cc.openxiot.device.api.accesspoint.server.endpoint.product.ProductService;
import cc.openxiot.device.api.accesspoint.replica.ReplicaService;
import cn.geekcity.xiot.spec.summary.Summary;
import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;

@ServerEndpoint(value = "/v1/{did}/{type}", configurator = XcpCertConfigurator.class)
@ApplicationScoped
public class XcpDeviceServer {

    @Inject
    Logger logger;

    @Inject
    ReplicaService replica;

    @Inject
    ProductService factory;

    @Inject
    ConsoleService console;

    @Inject
    XcpDeviceEndpointManager manager;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    Vertx vertx;

    private final StanzaCodec codec = StanzaCodec.getInstance();

    @OnOpen
    public void onOpen(
            Session session,
            EndpointConfig config,
            @PathParam("did") String did,
            @PathParam("type") String type
    ) {
        String cert = config.getUserProperties().containsKey("XCP_CLIENT_CN")
                ? (String) config.getUserProperties().get("XCP_CLIENT_CN")
                : "unknown";

        // 校验证书 CN 与设备 ID 一致（skip 本地开发环境无证书的情况）
        if (isRealCert(cert)) {
            String cn = extractCn(cert);
            if (cn != null && !cn.equals(did)) {
                logger.warnv("Reject device: CN mismatch, did={0}, cn={1}", did, cn);
                closeSession(session, "cert cn mismatch");
                return;
            }
        }

        console.activeOne(did)
                .chain(activated -> {
                    if (!activated) {
                        logger.warnv("Reject device, activation failed: did={0}", did);
                        closeSession(session, "device activation rejected");
                        return Uni.createFrom().item(true); // handled
                    }
                    logger.infov("XCP device connected: did={0}, sessionId={1}", did, session.getId());
                    return factory.newInstance(did, new Summary(type, true, "wss", null, did))
                            .chain(image -> {
                                if (image == null) {
                                    logger.warnv("Reject device, type not found from ProductCenter: did={0}, type={1}", did, type);
                                    closeSession(session, "device type not found");
                                    return Uni.createFrom().item(true); // handled, no re-close needed
                                }
                                return manager.add(new XcpDeviceEndpoint(vertx, replica.getIp(), session, image, codec));
                            });
                })
                .subscribe().with(
                        added -> {
                            if (!added) {
                                closeSession(session, "device already online");
                            }
                        },
                        e -> logger.errorv(e, "Failed to create device instance: did={0}", did)
                );
    }

    /** 是否为真实的证书 DN（而非占位符） */
    private static boolean isRealCert(String cert) {
        return !cert.equals("unknown") && !cert.equals("no-cert-header") && !cert.startsWith("parse-");
    }

    /** 从 DN 字符串中提取 CN 值，如 "CN=device-001,O=MyOrg" → "device-001" */
    private static String extractCn(String dn) {
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }

        return null;
    }

    private static void closeSession(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException e) {
            // ignore — session may already be closed
        }
    }

    @OnMessage
    public void onMessage(@PathParam("did") String did, Session session, String text) {
        // rate limit: per-device message throttle
        if (!rateLimiter.tryAcquire(did)) {
            logger.warnv("Rate limit exceeded for did={0}, closing session", did);

            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "rate limit exceeded"));
            } catch (Exception e) {
                logger.warnv("Error closing session (did: {0})", did, e);
            }

            return;
        }

        XcpDeviceEndpoint endpoint = manager.getEndpoint(did);
        if (endpoint != null) {
            endpoint.onReceive(text);
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("did") String did) {
        logger.infov("XCP device disconnected: did={0}, sessionId={1}", did, session.getId());
        if (manager.remove(session.getId())) {
            rateLimiter.remove(did);
        }
    }

    @OnError
    public void onError(Session session, @PathParam("did") String did, Throwable throwable) {
        logger.errorv(throwable, "XCP device error: did={0}, sessionId={1}", did, session.getId());
    }
}

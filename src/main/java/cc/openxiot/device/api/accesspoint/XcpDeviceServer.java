package cc.openxiot.device.api.accesspoint;

import cc.openxiot.device.api.accesspoint.configurator.XcpCertConfigurator;
import cc.openxiot.device.api.accesspoint.limiter.RateLimiter;
import cc.openxiot.device.api.accesspoint.session.XcpDeviceEndpoint;
import cc.openxiot.device.api.accesspoint.session.XcpDeviceEndpointManager;
import cc.openxiot.device.api.accesspoint.session.factory.XcpDeviceFactory;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

import java.io.IOException;

@ServerEndpoint(value = "/{did}/{type}", configurator = XcpCertConfigurator.class)
@ApplicationScoped
public class XcpDeviceServer {

    @Inject
    Logger logger;

    @Inject
    XcpDeviceFactory factory;

    @Inject
    XcpDeviceEndpointManager manager;

    @Inject
    RateLimiter rateLimiter;

    private final StanzaCodec codec = StanzaCodec.getInstance();

    @OnOpen
    public void onOpen(
            Session session,
            EndpointConfig config,
            @PathParam("did") String did,
            @PathParam("type") String type
    ) {
        String cert = config.getUserProperties().containsKey("XCP_CLIENT_CERT")
                ? (String) config.getUserProperties().get("XCP_CLIENT_CERT")
                : "unknown";

        // 校验证书 CN 与设备 ID 一致（skip 本地开发环境无证书的情况）
        if (isRealCert(cert)) {
            String cn = extractCn(cert);
            if (cn != null && !cn.equals(did)) {
                logger.warnv("Reject device: CN mismatch, did={0}, cn={1}", did, cn);
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "device id mismatch"));
                } catch (Exception e) {
                    logger.warnv("Error closing session", e);
                }
                return;
            }
        }

        logger.infov("XCP device connected: did={0}, sessionId={1}", did, session.getId());

        DeviceImage image = factory.newInstance(did, new Summary(type, true, "wss", null, did));
        if (image != null) {
            manager.register(new XcpDeviceEndpoint(session, image, codec));
        } else {
            logger.warnv("Reject device, type not found from ProductCenter: did={0}, type={1}", did, type);
            try {
                session.close();
            } catch (IOException e) {
                logger.warnv("Error closing session", e);
            }
        }
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

    @OnMessage
    public void onMessage(@PathParam("did") String did, Session session, String text) {
        // rate limit: per-device message throttle
        if (!rateLimiter.tryAcquire(session.getId())) {
            logger.warnv("Rate limit exceeded for did = {0}, closing session = (1)", did, session.getId());

            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "rate limit exceeded"));
            } catch (Exception e) {
                logger.warnv("Error closing session (did: {0})", did, e);
            }

            return;
        }
        
        logger.infov("onMessage: did = {0}, sessionId = {1}, text = {2}", did, session.getId(), text);

        XcpDeviceEndpoint endpoint = manager.getEndpoint(did);
        if (endpoint != null) {
            endpoint.onReceive(text);
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("did") String did) {
        logger.infov("XCP device disconnected: did = {0}, sessionId = {1}", did, session.getId());
        manager.unregister(session.getId());
    }

    @OnError
    public void onError(Session session, @PathParam("did") String did, Throwable throwable) {
        logger.errorv(throwable, "XCP device error: did = {0}, sessionId = {1}", did, session.getId());
    }
}

package cc.openxiot.device.api.accesspoint;

import cc.openxiot.device.api.accesspoint.configurator.XcpCertConfigurator;
import cc.openxiot.device.api.accesspoint.limiter.RateLimiter;
import cc.openxiot.device.api.accesspoint.session.DeviceSession;
import cc.openxiot.device.api.accesspoint.session.DeviceSessionManager;
import cn.geekcity.xiot.xcp.stanza.Stanza;
import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import cn.geekcity.xiot.xcp.stanza.iq.IQ;
import cn.geekcity.xiot.xcp.stanza.iq.IQError;
import cn.geekcity.xiot.xcp.stanza.iq.IQQuery;
import cn.geekcity.xiot.xcp.stanza.iq.IQResult;
import cn.geekcity.xiot.xcp.stanza.iq.basic.Ping;
import cn.geekcity.xiot.xcp.stanza.message.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@ServerEndpoint(value = "/{did}/{type}", configurator = XcpCertConfigurator.class)
@ApplicationScoped
public class XcpDeviceServer {

    @Inject
    Logger logger;

    @Inject
    DeviceSessionManager sessionManager;

    @Inject
    RateLimiter rateLimiter;

    private final StanzaCodec stanzaCodec = StanzaCodec.getInstance();

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
        session.setMaxIdleTimeout(60_000);
        sessionManager.register(did, new DeviceSession(did, session));
    }

    /** 是否为真实的证书 DN（而非占位符） */
    private static boolean isRealCert(String cert) {
        return !cert.equals("unknown")
                && !cert.equals("no-cert-header")
                && !cert.startsWith("parse-");
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
    public void onMessage(
            @PathParam("did") String did,
            @PathParam("type") String type,
            Session session,
            String text
    ) {
        // rate limit: per-device message throttle
        if (!rateLimiter.tryAcquire(did)) {
            logger.warnv("Rate limit exceeded for did={0}, closing session", did);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "rate limit exceeded"));
            } catch (Exception e) {
                logger.warnv("Error closing session", e);
            }
            return;
        }

        try {
            JsonObject json = new JsonObject(text);
            Stanza stanza = stanzaCodec.decode(json);

            if (stanza instanceof IQ) {
                handleIQ(session, did, (IQ) stanza);
            } else if (stanza instanceof Message) {
                logger.infov("Message stanza from did={0}: topic={1}", did, ((Message<?>) stanza).topic());
            }
        } catch (DecodeException e) {
            logger.warnv("Invalid JSON from did={0}: {1}", did, e.getMessage());
            sendError(session, "*", 400, "invalid json: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warnv("Invalid stanza from did={0}: {1}", did, e.getMessage());
        }
    }

    @OnClose
    public void onClose(
            Session session,
            @PathParam("did") String did,
            @PathParam("type") String type
    ) {
        logger.infov("XCP device disconnected: did={0}, sessionId={1}", did, session.getId());
        sessionManager.unregister(did);
        rateLimiter.remove(did);
    }

    @OnError
    public void onError(
            Session session,
            @PathParam("did") String did,
            @PathParam("type") String type,
            Throwable throwable
    ) {
        logger.errorv(throwable, "XCP device error: did={0}, sessionId={1}", did, session.getId());
    }

    private void handleIQ(Session session, String did, IQ iq) {
        logger.infov("IQ from did={0}: type={1}, method={2}", did, iq.type(), methodOf(iq));

        switch (iq.type()) {
            case QUERY -> handleQuery(session, did, (IQQuery) iq);
            case RESULT -> logger.infov("Result from did={0}: id={1}", did, iq.id());
            case ERROR -> logger.warnv("Error from did={0}: id={1}, status={2}", did, iq.id(),
                    iq instanceof IQError ? ((IQError) iq).status() : "?");
        }
    }

    private void handleQuery(Session session, String did, IQQuery query) {
        switch (query.method()) {
            case Ping.METHOD -> {
                Ping.Query ping = (Ping.Query) query;
                sendText(session, stanzaCodec.encode(ping.result()));
            }
            default ->
                logger.warnv("Unsupported query from did={0}: method={1}", did, query.method());
        }
    }

    private void sendText(Session session, JsonObject json) {
        session.getAsyncRemote().sendText(json.encode());
    }

    private void sendError(Session session, String id, int status, String description) {
        sendText(session, stanzaCodec.encode(new IQError(id, status, description)));
    }

    private static String methodOf(IQ iq) {
        if (iq instanceof IQQuery) {
            return ((IQQuery) iq).method();
        }
        if (iq instanceof IQResult) {
            return ((IQResult) iq).method();
        }
        return "";
    }
}

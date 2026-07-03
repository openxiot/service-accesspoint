package cc.openxiot.device.api.accesspoint;

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
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@ServerEndpoint(value = "/{did}", configurator = XcpDeviceServer.CertConfigurator.class)
@ApplicationScoped
public class XcpDeviceServer {

    @Inject
    Logger logger;

    @Inject
    DeviceSessionManager sessionManager;

    private final StanzaCodec stanzaCodec = StanzaCodec.getInstance();

    // 被 Quarkus 在应用启动时自动调用，往 Vert.x 路由链中插入一个前置处理器，
    // 在 WebSocket 握手前捕获客户端证书，通过 ThreadLocal 传递给 CertConfigurator
    void captureClientCert(@Observes Router router) {
        router.route().handler(ctx -> {
            String cert = "no-ssl";
            if (ctx.request().isSSL()) {
                try {
                    var sslSession = ctx.request().sslSession();
                    if (sslSession != null) {
                        var peerCerts = sslSession.getPeerCertificates();
                        if (peerCerts != null &&
                                peerCerts.length > 0 &&
                                peerCerts[0] instanceof java.security.cert.X509Certificate x509) {
                            cert = x509.getSubjectX500Principal().getName();
                        } else {
                            cert = "no-client-cert";
                        }
                    }
                } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
                    cert = "unverified: " + e.getMessage();
                }
            }
            certHolder.set(cert);
            ctx.next();
        });
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam("did") String did) {
        String cert = config.getUserProperties().containsKey("XCP_CLIENT_CERT")
                ? (String) config.getUserProperties().get("XCP_CLIENT_CERT")
                : "unknown";
        logger.infov("XCP device connected: did={0}, sessionId={1}, cert={2}", did, session.getId(), cert);
        session.setMaxIdleTimeout(60_000);
        sessionManager.register(did, new DeviceSession(did, cert, session));
    }

    @OnMessage
    public void onMessage(String text, Session session, @PathParam("did") String did) {
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
    public void onClose(Session session, @PathParam("did") String did) {
        logger.infov("XCP device disconnected: did={0}, sessionId={1}", did, session.getId());
        sessionManager.unregister(did);
    }

    @OnError
    public void onError(Session session, @PathParam("did") String did, Throwable throwable) {
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

    static final ThreadLocal<String> certHolder = new ThreadLocal<>();

    public static class CertConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
            String cert = certHolder.get();
            certHolder.remove();
            config.getUserProperties().put("XCP_CLIENT_CERT", cert != null ? cert : "unknown");
        }
    }
}

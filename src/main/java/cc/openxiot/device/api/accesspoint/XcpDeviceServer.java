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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@ServerEndpoint("/{did}")
@ApplicationScoped
public class XcpDeviceServer {

    @Inject
    Logger logger;

    private final StanzaCodec stanzaCodec = StanzaCodec.getInstance();

    @OnOpen
    public void onOpen(Session session, @PathParam("did") String did) {
        logger.infov("XCP device connected: did={0}, sessionId={1}", did, session.getId());
        session.setMaxIdleTimeout(60_000);
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
}

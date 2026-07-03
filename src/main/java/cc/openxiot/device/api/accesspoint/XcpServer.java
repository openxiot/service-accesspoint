package cc.openxiot.device.api.accesspoint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@ServerEndpoint("/ws/echo")
@ApplicationScoped
public class XcpServer {

    @Inject
    Logger logger;

    @OnOpen
    public void onOpen(Session session) {
        String instanceId = System.getenv("HOSTNAME");
        logger.infov("WebSocket opened: sessionId={0}, instanceId={1}", session.getId(), instanceId);
        session.getAsyncRemote().sendText("Connected to " + (instanceId != null ? instanceId : "unknown"));
    }

    @OnMessage
    public String onMessage(String message, Session session) {
        logger.infov("WebSocket message from {0}: {1}", session.getId(), message);
        return "echo: " + message;
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        logger.infov("WebSocket closed: sessionId={0}, reason={1}", session.getId(), reason);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.errorv(throwable, "WebSocket error: sessionId={0}", session.getId());
    }
}

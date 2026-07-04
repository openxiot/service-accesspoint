package cc.openxiot.device.api.accesspoint.session;

import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.notice.Notice;
import cn.geekcity.xiot.spec.operation.ActionOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import cn.geekcity.xiot.spec.status.Status;
import cn.geekcity.xiot.xcp.stanza.Stanza;
import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import cn.geekcity.xiot.xcp.stanza.iq.IQ;
import cn.geekcity.xiot.xcp.stanza.iq.IQError;
import cn.geekcity.xiot.xcp.stanza.iq.IQQuery;
import cn.geekcity.xiot.xcp.stanza.iq.IQResult;
import cn.geekcity.xiot.xcp.stanza.message.Message;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.websocket.Session;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XcpDeviceEndpoint {

    private static final Logger logger = Logger.getLogger(XcpDeviceEndpoint.class);

    private static final int DEFAULT_QUERY_TIMEOUT_MS = 3 * 1000;

    private final Vertx vertx;
    private final Session session;
    private final StanzaCodec codec;
    private final DeviceImage root;

    private final Map<String, Handler<IQQuery>> queryHandlers = new HashMap<>();
    private final Map<String, XcpResultHandler> resultHandlers = new HashMap<>();
    private final Map<String, Handler<Message<? extends Notice>>> messageHandlers = new HashMap<>();

    public XcpDeviceEndpoint(Vertx vertx, Session session, DeviceImage image, StanzaCodec codec) {
        this.vertx = vertx;
        this.session = session;
        this.root = image;
        this.codec = codec;

        this.session.setMaxIdleTimeout(60_000);
    }

    public String id() {
        return this.session.getId();
    }

    public DeviceImage root() {
        return root;
    }

    public void addQueryHandler(String method, Handler<IQQuery> handler) {
        queryHandlers.put(method, handler);
    }

    public void addMessageHandler(String topic, Handler<Message<? extends Notice>> handler) {
        messageHandlers.put(topic, handler);
    }

    public void send(IQQuery query, Handler<AsyncResult<IQ>> handler) {
        this.send(query, handler, DEFAULT_QUERY_TIMEOUT_MS);
    }

    public void send(IQQuery query, Handler<AsyncResult<IQ>> handler, int timeoutMS) {
        resultHandlers.put(query.id(), new XcpResultHandler(vertx, logger, query.id(), handler, timeoutMS, resultHandlers::remove));
        this.write(query);
    }

    public void send(IQResult result) {
        write(result);
    }

    public void send(IQError error) {
        write(error);
    }

    public void send(Message<? extends Notice> message) {
        write(message);
    }

    private void write(Stanza stanza) {
        JsonObject json = codec.encode(stanza);
        session.getAsyncRemote().sendText(json.encode());
    }

    public List<PropertyOperation> getProperties(String stanzaId, List<PropertyOperation> properties) {
        return null;
    }

    public List<PropertyOperation> setProperties(String stanzaId, List<PropertyOperation> properties) {
        return null;
    }

    public List<ActionOperation> invokeActions(String stanzaId, List<ActionOperation> actions) {
        return null;
    }

    public PropertyOperation getProperty(String stanzaId, PropertyOperation property) {
        return null;
    }

    public PropertyOperation setProperty(String stanzaId, PropertyOperation property) {
        return null;
    }

    public ActionOperation invokeAction(String stanzaId, ActionOperation action) {
        return null;
    }


    public void onReceive(String text) {
        try {
            JsonObject json = new JsonObject(text);
            handleStanza(codec.decode(json));
        } catch (DecodeException e) {
            this.send(new IQError("*", Status.INTERNAL_ERROR, "invalid json: " + e.getMessage()));
        }
    }

    private void handleStanza(Stanza stanza) {
        switch (stanza.stanzaType()) {
            case IQ:
                handleIQ((IQ) stanza);
                break;

            case MESSAGE:
                handleMessage((Message<? extends Notice>) stanza);
                break;

            default:
                break;
        }
    }

    private void handleIQ(IQ iq) {
        switch (iq.type()) {
            case QUERY:
                handleQuery((IQQuery) iq);
                break;

            case RESULT:
                handleResult((IQResult) iq);
                break;

            case ERROR:
                handleError((IQError) iq);
                break;

            default:
                logger.info("invalid IQ: " + iq.type());
                break;
        }
    }

    private void handleQuery(IQQuery query) {
        Handler<IQQuery> handler = queryHandlers.get(query.method());
        if (handler != null) {
            handler.handle(query);
        } else {
            logger.info("Query Handler not found: " + query.method());
            this.send(query.error(Status.QUERY_NOT_SUPPORTED, "Query Handler not found"));
        }
    }

    private void handleResult(IQResult result) {
        XcpResultHandler handler = resultHandlers.remove(result.id());
        if (handler != null) {
            handler.handle(Future.succeededFuture(result));
        } else {
            logger.info("Result Handler not found: " + result.id());
        }
    }

    private void handleError(IQError error) {
        XcpResultHandler handle = resultHandlers.remove(error.id());
        if (handle != null) {
            handle.handle(Future.failedFuture(new IotError(error.status(), error.description())));
        } else {
            logger.info("Error Handler not found: " + error.id());
        }
    }

    private void handleMessage(Message<? extends Notice> message) {
        // 重置设备消息的时间戳
        message.payload().timestamp(System.currentTimeMillis());

        Handler<Message<? extends Notice>> handler = messageHandlers.get(message.topic());
        if (handler != null) {
            handler.handle(message);
        } else {
            logger.info("Message Handler not found: " + message.topic());
        }
    }
}

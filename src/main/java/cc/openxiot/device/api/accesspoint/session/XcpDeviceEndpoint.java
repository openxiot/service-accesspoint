package cc.openxiot.device.api.accesspoint.session;

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
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import org.jboss.logging.Logger;

import java.util.List;

public class XcpDeviceEndpoint {

    @Inject
    Logger logger;

    private static final int DEFAULT_QUERY_TIMEOUT_MS = 3 * 1000;

    private final Session session;
    private final StanzaCodec codec;
    private final DeviceImage root;

    public XcpDeviceEndpoint(Session session, DeviceImage image, StanzaCodec codec) {
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
//        Handler<IQQuery> handler = queryHandlers.get(query.method());
//        if (handler != null) {
//            try {
//                handler.handle(query);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        } else {
//            logger.info("Query Handler not found: " + query.method());
//            this.send(query.error(Status.QUERY_NOT_SUPPORTED, "Query Handler not found"));
//        }
    }

    private void handleResult(IQResult result) {
//        ResultHandler handler = resultHandlers.remove(result.id());
//        if (handler != null) {
//            handler.handle(Future.succeededFuture(result));
//        } else {
//            logger.info("Result Handler not found: " + result.id());
//        }
    }

    private void handleError(IQError error) {
//        ResultHandler handle = resultHandlers.remove(error.id());
//        if (handle != null) {
//            try {
//                handle.handle(Future.failedFuture(new IotError(error.status(), error.description())));
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        } else {
//            logger.info("Error Handler not found: " + error.id());
//        }
    }

    private void handleMessage(Message<? extends Notice> message) {
        // 重置设备消息的时间戳
//        message.payload().timestamp(System.currentTimeMillis());
//        Handler<Message<? extends Notice>> handler = messageHandlers.get(message.topic());
//        if (handler != null) {
//            try {
//                handler.handle(message);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        } else {
//            logger.info("Message Handler not found: " + message.topic());
//        }
//
//        send(message);
    }

    public void send(IQQuery query) {
        this.send(query, DEFAULT_QUERY_TIMEOUT_MS);
    }

    public void send(IQQuery query, int timeout) {
        // session.getAsyncRemote().sendText("hello");
    }

    public void send(IQResult result) {

    }

    public void send(IQError error) {

    }

    public void send(Message<? extends Notice> message) {

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
}

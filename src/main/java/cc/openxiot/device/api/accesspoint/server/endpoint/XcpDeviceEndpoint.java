package cc.openxiot.device.api.accesspoint.server.endpoint;

import cn.geekcity.xiot.spec.constant.Constant;
import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.notice.Notice;
import cn.geekcity.xiot.spec.operation.ActionOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import cn.geekcity.xiot.spec.ownership.DeviceOwner;
import cn.geekcity.xiot.spec.status.Status;
import cn.geekcity.xiot.xcp.stanza.Stanza;
import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import cn.geekcity.xiot.xcp.stanza.iq.IQ;
import cn.geekcity.xiot.xcp.stanza.iq.IQError;
import cn.geekcity.xiot.xcp.stanza.iq.IQQuery;
import cn.geekcity.xiot.xcp.stanza.iq.IQResult;
import cn.geekcity.xiot.xcp.stanza.iq.basic.Byebye;
import cn.geekcity.xiot.xcp.stanza.iq.basic.Ping;
import cn.geekcity.xiot.xcp.stanza.iq.device.control.GetProperties;
import cn.geekcity.xiot.xcp.stanza.iq.device.control.InvokeActions;
import cn.geekcity.xiot.xcp.stanza.iq.device.control.SetProperties;
import cn.geekcity.xiot.xcp.stanza.iq.device.key.GetAccessKey;
import cn.geekcity.xiot.xcp.stanza.iq.device.key.SetAccessKey;
import cn.geekcity.xiot.xcp.stanza.iq.device.notify.EventOccurred;
import cn.geekcity.xiot.xcp.stanza.iq.device.notify.PropertiesChanged;
import cn.geekcity.xiot.xcp.stanza.iq.owner.manager.AddOwner;
import cn.geekcity.xiot.xcp.stanza.iq.owner.manager.GetOwners;
import cn.geekcity.xiot.xcp.stanza.iq.owner.manager.RemoveOwner;
import cn.geekcity.xiot.xcp.stanza.message.Message;
import io.vertx.core.*;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class XcpDeviceEndpoint {

    @Inject
    XcpDeviceEndpointHandler handler;

    private static final Logger logger = Logger.getLogger(XcpDeviceEndpoint.class);
    private static final int DEFAULT_QUERY_TIMEOUT_MS = 3_000;
    private static final int DEFAULT_MAX_IDLE_TIMEOUT_MS = 30_000;

    private final Vertx vertx;
    private final String replicaIp;
    private final Session session;
    private final StanzaCodec codec;
    private final DeviceImage root;

    private final Map<String, Handler<IQQuery>> queryHandlers = new HashMap<>();
    private final Map<String, XcpResultHandler> resultHandlers = new HashMap<>();
    private final Map<String, Handler<Message<? extends Notice>>> messageHandlers = new HashMap<>();

    public XcpDeviceEndpoint(Vertx vertx, String ip, Session session, DeviceImage image, StanzaCodec codec) {
        this.vertx = vertx;
        this.replicaIp = ip;
        this.session = session;
        this.root = image;
        this.codec = codec;

        this.session.setMaxIdleTimeout(DEFAULT_MAX_IDLE_TIMEOUT_MS);

        this.addQueryHandler(Ping.METHOD, this::onPing);
        this.addQueryHandler(GetAccessKey.METHOD, this::onGetAccessKey);
        this.addQueryHandler(SetAccessKey.METHOD, this::onSetAccessKey);
        this.addQueryHandler(AddOwner.METHOD, this::onAddOwner);
        this.addQueryHandler(RemoveOwner.METHOD, this::onRemoveOwner);
        this.addQueryHandler(GetOwners.METHOD, this::onGetOwners);
        this.addQueryHandler(PropertiesChanged.METHOD, this::onPropertiesChanged);
        this.addQueryHandler(EventOccurred.METHOD, this::onEventOccurred);
        this.addQueryHandler(Byebye.METHOD, this::onBye);
    }

    public String replicaIp() {
        return this.replicaIp;
    }

    public String id() {
        return this.session.getId();
    }

    public Session session() {
        return session;
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
        String text = json.encode();

        logger.infov("[{0}] send: {1}", root.did(), text);

        session.getAsyncRemote().sendText(text, result -> {
            if (!result.isOK()) {
                logger.errorv("Failed to send stanza to {0}: {1}", id(), result.getException().getMessage());
            }
        });
    }

    /**
     * get properties
     * @param stanzaId stanzaId
     * @param properties the properties to be got
     * @return  a result for execute
     */
    public Future<List<PropertyOperation>> getProperties(String stanzaId, List<PropertyOperation> properties) {
        Promise<List<PropertyOperation>> promise = Promise.promise();

        root.tryRead(properties);

        List<PropertyOperation> normalProperties = new ArrayList<>();

        for (PropertyOperation property : properties) {
            if (property.isNotError()) {
                normalProperties.add(property);
            }
        }

        if (normalProperties.isEmpty()) {
            promise.complete(properties);
        } else {
            GetProperties.Query query = new GetProperties.Query(stanzaId, normalProperties);
            this.send(query, ar -> {
                if (ar.succeeded()) {
                    Map<String, PropertyOperation> results = ((GetProperties.Result) ar.result()).properties().stream()
                            .collect(Collectors.toMap((x -> x.pid().toString()), Function.identity()));

                    for (PropertyOperation p : normalProperties) {
                        PropertyOperation result = results.get(p.pid().toString());
                        if (result != null) {
                            p.status(result.status());
                            p.description(result.description());
                            p.value(result.value());
                        } else {
                            p.status(Status.INTERNAL_ERROR);
                            p.description("response not contains property" );
                        }
                    }

                    promise.complete(properties);
                } else {
                    promise.fail(ar.cause());
                }
            });
        }

        return promise.future();
    }

    /**
     * get properties
     * @param stanzaId stanzaId
     * @param properties the properties to be set
     * @return  a result for execute
     */
    public Future<List<PropertyOperation>> setProperties(String stanzaId, List<PropertyOperation> properties) {
        Promise<List<PropertyOperation>> promise = Promise.promise();

        root.tryWrite(properties, false);

        List<PropertyOperation> normalProperties = new ArrayList<>();

        for (PropertyOperation property : properties) {
            if (property.isNotError()) {
                normalProperties.add(property);
            }
        }

        if (normalProperties.isEmpty()) {
            promise.complete(properties);
        } else {
            SetProperties.Query query = new SetProperties.Query(stanzaId, normalProperties);
            this.send(query, ar -> {
                if (ar.succeeded()) {
                    Map<String, PropertyOperation> results = ((SetProperties.Result) ar.result()).properties().stream()
                            .collect(Collectors.toMap((x -> x.pid().toString()), Function.identity()));

                    for (PropertyOperation p : normalProperties) {
                        PropertyOperation result = results.get(p.pid().toString());
                        if (result != null) {
                            p.status(result.status());
                            p.description(result.description());
                        } else {
                            p.status(Status.INTERNAL_ERROR);
                            p.description("response not contains property" );
                        }
                    }

                    promise.complete(properties);
                } else {
                    promise.fail(ar.cause());
                }
            });
        }

        return promise.future();
    }

    /**
     * invoke actions
     * @param stanzaId stanzaId
     * @param actions the actions to be invocation
     * @return  a result for execute
     */
    public Future<List<ActionOperation>> invokeActions(String stanzaId, List<ActionOperation> actions) {
        Promise<List<ActionOperation>> promise = Promise.promise();

        root.tryInvoke(actions);

        List<ActionOperation> normalActions = new ArrayList<>();

        for (ActionOperation action : actions) {
            if (action.isNotError()) {
                normalActions.add(action);
            }
        }

        if (normalActions.isEmpty()) {
            promise.complete(actions);
        } else {
            InvokeActions.Query query = new InvokeActions.Query(stanzaId, normalActions);
            this.send(query, ar -> {
                if (ar.succeeded()) {
                    Map<String, ActionOperation> results = ((InvokeActions.Result) ar.result()).actions().stream()
                            .collect(Collectors.toMap((x -> x.aid().toString()), Function.identity()));

                    for (ActionOperation p : normalActions) {
                        ActionOperation result = results.get(p.aid().toString());
                        if (result != null) {
                            p.status(result.status());
                            p.description(result.description());
                            p.out(result.out().values());
                        } else {
                            p.status(Status.INTERNAL_ERROR);
                            p.description("response not contains action" );
                        }
                    }

                    promise.complete(actions);
                } else {
                    promise.fail(ar.cause());
                }
            });
        }

        return promise.future();
    }

    /**
     * get property
     * @param stanzaId stanzaId
     * @param property the property to be got
     * @return  a result for execute
     */
    public Future<PropertyOperation> getProperty(String stanzaId, PropertyOperation property) {
        return getProperties(stanzaId, Collections.singletonList(property)).map(List::getFirst);
    }

    /**
     * get property
     * @param stanzaId stanzaId
     * @param property the property to be set
     * @return  a result for execute
     */
    public Future<PropertyOperation> setProperty(String stanzaId, PropertyOperation property) {
        return setProperties(stanzaId, Collections.singletonList(property)).map(List::getFirst);
    }

    /**
     * invoke action
     * @param stanzaId stanzaId
     * @param action the action to be invocation
     * @return  a result for execute
     */
    public Future<ActionOperation> invokeAction(String stanzaId, ActionOperation action) {
        return invokeActions(stanzaId, Collections.singletonList(action)).map(List::getFirst);
    }

    public void onReceive(String text) {
        try {
            logger.infov("[{0}] recv: {1}", root.did(), text);

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

    private void onGetAccessKey(IQQuery query) {
        GetAccessKey.Query q = (GetAccessKey.Query)query;

        try {
            String value = handler.getAccessKey(root.did());
            this.write(q.result(value));
        } catch (IotError e) {
            this.write(q.error(e.status(), e.description()));
        }
    }

    private void onSetAccessKey(IQQuery query) {
        SetAccessKey.Query q = (SetAccessKey.Query)query;

        if (q.key().length() < Constant.ACCESS_KEY_MIN_LENGTH) {
            this.write(q.error(Status.ACCESS_KEY_INVALID, "access-key too short"));
            return;
        }

        handler.setAccessKey(root.did(), q.key());

        this.write(q.result());

        handler.onAccessKeyChanged(this, root.did(), q.key(), q.id());
    }

    private void onGetOwners(IQQuery query) {
        GetOwners.Query q = (GetOwners.Query)query;

        List<DeviceOwner> owners = handler.getOwners(this.root.did());
        this.write(q.result(owners));
    }

    private void onAddOwner(IQQuery query) {
        AddOwner.Query q = (AddOwner.Query)query;

        try {
            handler.addOwner(this.root.did(), q.owner());
            this.write(q.result());
        } catch (IotError e) {
            this.write(q.error(e.status(), e.description()));
        }
    }

    private void onRemoveOwner(IQQuery query) {
        RemoveOwner.Query q = (RemoveOwner.Query)query;

        handler.removeOwner(this.root.did(), q.owner());
        this.write(q.result());
    }

    private void onPropertiesChanged(IQQuery query) {
        logger.info("onPropertiesChanged: " + query.id());

        PropertiesChanged.Query q = (PropertiesChanged.Query) query;

        root.onPropertiesChanged(q.properties());

        List<PropertyOperation> list = q.properties().stream().filter(p -> p.status() == 0).collect(Collectors.toList());
        if (!list.isEmpty()) {
            handler.onPropertiesChanged(this, list, q.id());
        }

        this.write(q.result(q.properties()));
    }

    private void onEventOccurred(IQQuery query) {
        logger.info("onEventOccurred: " + query.id());

        EventOccurred.Query q = (EventOccurred.Query)query;

        root.tryEventOccurred(q.event());

        if (q.event().isNotError()) {
            handler.onEventOccurred(this, q.event(), q.id());
            this.write(q.result());
        } else {
            this.write(q.error(q.event().status(), q.event().description()));
        }
    }

    private void onBye(IQQuery query) {
        logger.info("onBye: " + query.id());

        handler.onInactive(this);
    }

    private void onPing(IQQuery query) {
        Ping.Query q = (Ping.Query) query;
        send(q.result());
    }
}

package cc.openxiot.device.api.accesspoint.server.endpoint;

import cc.openxiot.device.api.accesspoint.server.endpoint.console.ConsoleService;
import cc.openxiot.device.api.accesspoint.server.endpoint.factory.ProductService;
import cn.geekcity.xiot.spec.constant.Constant;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.notice.Notice;
import cn.geekcity.xiot.spec.operation.ActionOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import cn.geekcity.xiot.spec.status.AbstractStatus;
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
import cn.geekcity.xiot.xcp.stanza.iq.device.manager.GetChildren;
import cn.geekcity.xiot.xcp.stanza.iq.device.notify.*;
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

import io.smallrye.mutiny.Uni;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class XcpDeviceEndpoint {

    @Inject
    XcpDeviceEndpointHandler handler;

    @Inject
    ProductService factory;

    @Inject
    ConsoleService console;

    private static final Logger logger = Logger.getLogger(XcpDeviceEndpoint.class);
    private static final int DEFAULT_QUERY_TIMEOUT_MS = 3_000;
    private static final int DEFAULT_MAX_IDLE_TIMEOUT_MS = 30_000;

    private final Vertx vertx;
    private final String replicaIp;
    private final Session session;
    private final StanzaCodec codec;
    private final DeviceImage root;
    private final String id;
    private long stanzaId = 1;

    private final Map<String, Handler<IQQuery>> queryHandlers = new HashMap<>();
    private final Map<String, XcpResultHandler> resultHandlers = new HashMap<>();
    private final Map<String, Handler<Message<? extends Notice>>> messageHandlers = new HashMap<>();

    public XcpDeviceEndpoint(Vertx vertx, String ip, Session session, DeviceImage image, StanzaCodec codec) {
        this.vertx = vertx;
        this.id = generateId();
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
        this.addQueryHandler(ChildrenAdded.METHOD, this::onChildrenAdded);
        this.addQueryHandler(ChildrenRemoved.METHOD, this::onChildrenRemoved);
        this.addQueryHandler(SummaryChanged.METHOD, this::onSummaryChanged);

        // TODO: 这里加一个逻辑，申请子设备ID，允许网关给子设备申请ID
    }

    public void initialize() {
        this.getProperties(root);
        this.getChildren();
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

    public Future<IQ> send(IQQuery query) {
        return this.send(query, DEFAULT_QUERY_TIMEOUT_MS);
    }

    public Future<IQ> send(IQQuery query, int timeoutMS) {
        Promise<IQ> promise = Promise.promise();
        resultHandlers.put(query.id(), new XcpResultHandler(vertx, logger, query.id(), promise, timeoutMS, resultHandlers::remove));
        this.write(query);
        return promise.future();
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

    private void getProperties(DeviceImage device) {
        String stanzaId = nextStanzaId();
        List<PropertyOperation> properties = device.getReadablePropertyIDs().stream().map(PropertyOperation::new).collect(Collectors.toList());
        GetProperties.Query query = new GetProperties.Query(stanzaId, properties);

        this.send(query).onComplete(ar -> {
            List<PropertyOperation> results = new ArrayList<>();

            if (ar.succeeded()) {
                logger.info("getProperties succeeded: " + device.did());
                results = ((GetProperties.Result) ar.result()).properties();
            } else {
                logger.info("getProperties failed: " + device.did() + ", cause: " + ar.cause());

                Throwable t = ar.cause();
                if (t instanceof IotError e) {
                    for (PropertyOperation p : query.properties()) {
                        p.status(e.status()).description(e.description());
                        results.add(p);
                    }
                } else {
                    for (PropertyOperation p : query.properties()) {
                        p.status(Status.INTERNAL_ERROR).description(t.getMessage());
                        results.add(p);
                    }
                }
            }

            handler.onPropertiesChanged(this, results, stanzaId);
        });
    }

    private void getChildren() {
        String stanzaId = nextStanzaId();
        GetChildren.Query query = new GetChildren.Query(stanzaId, root.did());

        this.send(query).onComplete(ar -> {
            if (ar.succeeded()) {
                if (ar.result() instanceof GetChildren.Result) {
                    List<Device> children = ((GetChildren.Result) ar.result()).children();
                    onChildrenActive(children);
                } else if (ar.result() instanceof IQError error) {
                    logger.infov("getChildren error: {0} {1}", error.status(), error.description());
                } else {
                    logger.info("getChildren error, invalid result: " + ar.result().getClass().getSimpleName());
                }
            } else {
                Throwable e = ar.cause();

                if (e instanceof IotError error) {
                    logger.infov("getChildren failed: {0}, {1}", error.status(), error.description());
                } else {
                    logger.info("getChildren failed: " + e);
                }
            }
        });
    }

    private void onChildrenActive(List<Device> children) {
        List<String> dids = children.stream().map(Device::did).toList();

        console.probeMany(dids)
                .chain(found -> {
                    List<Device> validated = children.stream()
                            .filter(x -> found.contains(x.did()))
                            .toList();
                    return factory.newInstances(validated);
                })
                .chain(list -> {
                    if (list.isEmpty()) return Uni.createFrom().voidItem();
                    this.root.add(list);
                    return handler.onChildrenActive(this, list)
                            .invoke(v -> {
                                for (DeviceImage image : list) {
                                    getProperties(image);
                                }
                            });
                })
                .subscribe().with(v -> {});
    }

    /**
     * get properties
     * @param stanzaId stanzaId
     * @param properties the properties to be got
     * @return  a result for execute
     */
    public Future<List<PropertyOperation>> getProperties(String stanzaId, List<PropertyOperation> properties) {
        root.tryRead(properties);

        List<PropertyOperation> normalProperties = properties.stream()
                .filter(PropertyOperation::isNotError)
                .toList();

        if (normalProperties.isEmpty()) {
            return Future.succeededFuture(properties);
        }

        return this.send(new GetProperties.Query(stanzaId, normalProperties))
                .map(result -> {
                    Map<String, PropertyOperation> results = ((GetProperties.Result) result).properties().stream()
                            .collect(Collectors.toMap(x -> x.pid().toString(), Function.identity()));

                    for (PropertyOperation p : normalProperties) {
                        PropertyOperation r = results.get(p.pid().toString());
                        if (r != null) {
                            p.status(r.status());
                            p.description(r.description());
                            p.value(r.value());
                        } else {
                            p.status(Status.INTERNAL_ERROR);
                            p.description("response not contains property");
                        }
                    }
                    return properties;
                });
    }

    /**
     * get properties
     * @param stanzaId stanzaId
     * @param properties the properties to be set
     * @return  a result for execute
     */
    public Future<List<PropertyOperation>> setProperties(String stanzaId, List<PropertyOperation> properties) {
        root.tryWrite(properties, false);

        List<PropertyOperation> normalProperties = properties.stream()
                .filter(PropertyOperation::isNotError)
                .toList();

        if (normalProperties.isEmpty()) {
            return Future.succeededFuture(properties);
        }

        return this.send(new SetProperties.Query(stanzaId, normalProperties))
                .map(result -> {
                    Map<String, PropertyOperation> results = ((SetProperties.Result) result).properties().stream()
                            .collect(Collectors.toMap(x -> x.pid().toString(), Function.identity()));

                    for (PropertyOperation p : normalProperties) {
                        PropertyOperation r = results.get(p.pid().toString());
                        if (r != null) {
                            p.status(r.status());
                            p.description(r.description());
                        } else {
                            p.status(Status.INTERNAL_ERROR);
                            p.description("response not contains property");
                        }
                    }
                    return properties;
                });
    }

    /**
     * invoke actions
     * @param stanzaId stanzaId
     * @param actions the actions to be invocation
     * @return  a result for execute
     */
    public Future<List<ActionOperation>> invokeActions(String stanzaId, List<ActionOperation> actions) {
        root.tryInvoke(actions);

        List<ActionOperation> normalActions = actions.stream()
                .filter(ActionOperation::isNotError)
                .toList();

        if (normalActions.isEmpty()) {
            return Future.succeededFuture(actions);
        }

        return this.send(new InvokeActions.Query(stanzaId, normalActions))
                .map(result -> {
                    Map<String, ActionOperation> results = ((InvokeActions.Result) result).actions().stream()
                            .collect(Collectors.toMap(x -> x.aid().toString(), Function.identity()));

                    for (ActionOperation p : normalActions) {
                        ActionOperation r = results.get(p.aid().toString());
                        if (r != null) {
                            p.status(r.status());
                            p.description(r.description());
                            p.out(r.out().values());
                        } else {
                            p.status(Status.INTERNAL_ERROR);
                            p.description("response not contains action");
                        }
                    }
                    return actions;
                });
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

        handler.getAccessKey(root.did())
                .subscribe().with(
                        value -> this.write(q.result(value)),
                        e -> {
                            if (e instanceof IotError ie) {
                                this.write(q.error(ie.status(), ie.description()));
                            } else {
                                this.write(q.error(Status.INTERNAL_ERROR, e.getMessage()));
                            }
                        }
                );
    }

    private void onSetAccessKey(IQQuery query) {
        SetAccessKey.Query q = (SetAccessKey.Query)query;

        if (q.key().length() < Constant.ACCESS_KEY_MIN_LENGTH) {
            this.write(q.error(Status.ACCESS_KEY_INVALID, "access-key too short"));
            return;
        }

        this.write(q.result());

        handler.setAccessKey(root.did(), q.key())
                .invoke(v -> handler.onAccessKeyChanged(this, root.did(), q.key(), q.id()))
                .subscribe().with(v -> {}, e -> logger.errorv(e, "setAccessKey failed"));
    }

    private void onGetOwners(IQQuery query) {
        GetOwners.Query q = (GetOwners.Query)query;

        handler.getOwners(this.root.did())
                .subscribe().with(
                        owners -> this.write(q.result(owners)),
                        e -> this.write(q.error(Status.INTERNAL_ERROR, e.getMessage()))
                );
    }

    private void onAddOwner(IQQuery query) {
        AddOwner.Query q = (AddOwner.Query)query;

        handler.addOwner(this.root.did(), q.owner())
                .subscribe().with(
                        v -> this.write(q.result()),
                        e -> {
                            if (e instanceof IotError ie) {
                                this.write(q.error(ie.status(), ie.description()));
                            } else {
                                this.write(q.error(Status.INTERNAL_ERROR, e.getMessage()));
                            }
                        }
                );
    }

    private void onRemoveOwner(IQQuery query) {
        RemoveOwner.Query q = (RemoveOwner.Query)query;

        handler.removeOwner(this.root.did(), q.owner())
                .subscribe().with(
                        v -> this.write(q.result()),
                        e -> this.write(q.error(Status.INTERNAL_ERROR, e.getMessage()))
                );
    }

    private void onPropertiesChanged(IQQuery query) {
        logger.info("onPropertiesChanged: " + query.id());

        PropertiesChanged.Query q = (PropertiesChanged.Query) query;

        root.onPropertiesChanged(q.properties());

        List<PropertyOperation> list = q.properties().stream().filter(AbstractStatus::isNotError).toList();
        if (!list.isEmpty()) {
            handler.onPropertiesChanged(this, list, q.id()).subscribe().with(v -> {});
        }

        this.write(q.result(q.properties()));
    }

    private void onEventOccurred(IQQuery query) {
        logger.info("onEventOccurred: " + query.id());

        EventOccurred.Query q = (EventOccurred.Query)query;

        root.tryEventOccurred(q.event());

        if (q.event().isNotError()) {
            handler.onEventOccurred(this, q.event(), q.id()).subscribe().with(v -> {});
            this.write(q.result());
        } else {
            this.write(q.error(q.event().status(), q.event().description()));
        }
    }

    private void onBye(IQQuery query) {
        logger.info("onBye: " + query.id());

        handler.onInactive(this).subscribe().with(v -> {});
    }

    private void onChildrenAdded(IQQuery query) {
        ChildrenAdded.Query q = (ChildrenAdded.Query) query;
        send(q.result());

        factory.newInstances(q.children())
                .chain(list -> {
                    if (list.isEmpty()) return Uni.createFrom().voidItem();
                    this.root.add(list);
                    return handler.onChildrenAdded(this, list)
                            .invoke(v -> {
                                for (DeviceImage image : list) {
                                    getProperties(image);
                                }
                            });
                })
                .subscribe().with(v -> {});
    }

    private void onChildrenRemoved(IQQuery query) {
        ChildrenRemoved.Query q = (ChildrenRemoved.Query) query;
        send(q.result());

        List<DeviceImage> list = new ArrayList<>();

        for (String childId : q.children()) {
            DeviceImage device = root.remove(childId);
            if (device != null) {
                list.add(device);
            } else {
                logger.warn("remove child error, childId: " + childId);
            }
        }

        handler.onChildrenRemoved(this, list).subscribe().with(v -> {});
    }

    private void onSummaryChanged(IQQuery query) {
        SummaryChanged.Query q = (SummaryChanged.Query) query;

        DeviceImage node = root.getNode(q.did());
        node.summary(q.summary());

        handler.onSummaryChanged(this, q.did(), q.summary(), q.id()).subscribe().with(v -> {});
    }

    private void onPing(IQQuery query) {
        Ping.Query q = (Ping.Query) query;
        send(q.result());
    }

    private String generateId() {
        String[] fields = UUID.randomUUID().toString().split("-");
        return fields[fields.length - 1];
    }

    private String nextStanzaId() {
        return this.id + "#" + stanzaId++;
    }
}

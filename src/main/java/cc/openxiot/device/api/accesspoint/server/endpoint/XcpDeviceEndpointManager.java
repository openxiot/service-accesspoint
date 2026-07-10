package cc.openxiot.device.api.accesspoint.server.endpoint;

import cc.openxiot.device.api.accesspoint.server.endpoint.console.ConsoleService;
import cc.openxiot.device.api.accesspoint.server.endpoint.product.ProductService;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.notice.Notice;
import cn.geekcity.xiot.spec.operation.ActionOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import cn.geekcity.xiot.spec.shadow.Shadow;
import cn.geekcity.xiot.spec.status.Status;
import cn.geekcity.xiot.xcp.stanza.iq.IQ;
import cn.geekcity.xiot.xcp.stanza.iq.device.control.*;
import cn.geekcity.xiot.xcp.stanza.message.Message;
import cc.openxiot.device.api.accesspoint.server.endpoint.detector.DetectorService;
import cc.openxiot.device.db.registry.DeviceRegistryRepository;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class XcpDeviceEndpointManager {

    @Inject
    Logger logger;

    @Inject
    XcpDeviceEndpointHandler handler;

    @Inject
    ProductService product;

    @Inject
    ConsoleService console;

    @Inject
    DeviceRegistryRepository registry;

    @Inject
    DetectorService detector;

    private final ConcurrentHashMap<String, XcpDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> devices = new ConcurrentHashMap<>();

    public Uni<Boolean> add(XcpDeviceEndpoint endpoint) {
        return isOnline(endpoint.root().did())
                .chain(online -> {
                    if (online) {
                        logger.infov("device already online: {0}", endpoint.root().did());
                        return Uni.createFrom().item(false);
                    }

                    endpoint.handler(handler);
                    endpoint.product(product);
                    endpoint.console(console);

                    endpoints.put(endpoint.id(), endpoint);
                    save(endpoint.root(), endpoint.id());
                    endpoint.root().summary().lastOnline(new Date());

                    return handler.onActive(endpoint)
                            .chain(v -> {
                                if (!endpoint.session().isOpen()) {
                                    XcpDeviceEndpoint removed = endpoints.remove(endpoint.id());
                                    if (removed != null) {
                                        removeDeviceTree(removed.root());
                                        handler.onInactive(removed).subscribe().with(x -> {
                                        });
                                    }
                                }
                                endpoint.initialize();
                                return Uni.createFrom().item(true);
                            });
                });
    }

    private void save(DeviceImage device, String endpointId) {
        logger.infov("save: {0} => {1}", device.did(), endpointId);

        devices.put(device.did(), endpointId);

        for (DeviceImage child : device.children().values()) {
            save(child, endpointId);
        }
    }

    public boolean remove(String id) {
        XcpDeviceEndpoint endpoint = endpoints.remove(id);
        if (endpoint != null) {
            removeDeviceTree(endpoint.root());
            handler.onInactive(endpoint).subscribe().with(v -> {
            });
            return true;
        }
        return false;
    }

    private void removeDeviceTree(DeviceImage device) {
        devices.remove(device.did());
        for (DeviceImage child : device.children().values()) {
            removeDeviceTree(child);
        }
    }

    /**
     * 判断设备是否在线。
     * <p>先查本地 devices 内存表，命中则一定在本实例在线；
     * 不命中则查 DB，DB 说在线则探测原接入点确认是否真在线（防僵尸）。</p>
     */
    public Uni<Boolean> isOnline(String did) {
        if (devices.containsKey(did)) {
            return Uni.createFrom().item(true);
        }

        return registry.get(did)
                .chain(device -> {
                    if (device == null || !device.online) {
                        return Uni.createFrom().item(false);
                    }
                    return detector.probe(did, device.accessPoint)
                            .onItem().invoke(online -> {
                                if (!online) {
                                    logger.warnv("device is zombie (DB online but probe failed), did={0}, accessPoint={1}", did, device.accessPoint);
                                }
                            });
                });
    }

    public XcpDeviceEndpoint getEndpoint(String did) {
        String endpointId = devices.get(did);
        return endpointId != null ? endpoints.get(endpointId) : null;
    }

    public Collection<XcpDeviceEndpoint> getEndpoints() {
        return endpoints.values();
    }

    public List<Device> getDevices() {
        List<Device> list = new ArrayList<>();

        for (XcpDeviceEndpoint endpoint : endpoints.values()) {
            list.add(new Device(endpoint.root().did(), endpoint.root().summary()));

            for (DeviceImage child : endpoint.root().children().values()) {
                list.add(new Device(child.did(), child.summary()));
            }
        }

        return list;
    }

    public boolean probe(String did) {
        return devices.contains(did);
    }

    public List<Shadow> getShadow(String did) {
        String endpointId = devices.get(did);
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                return endpoint.root().getShadow(did);
            } else {
                logger.errorv("endpoint not found: {0}", endpointId);
            }
        } else {
            logger.errorv("endpointId not found: {0}", did);
        }

        return Collections.emptyList();
    }

    void send(String did, Message<? extends Notice> message) {
        String endpointId = devices.get(did);
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.send(message);
            } else {
                logger.errorv("endpoint not found: {0}", endpointId);
            }
        } else {
            logger.errorv("endpointId not found: {0}", did);
        }
    }

//    public Future<List<PropertyOperation>> getProperties(String traceId, List<PropertyOperation> operations) {
//        Promise<List<PropertyOperation>> promise = Promise.promise();
//
//        Map<String, List<PropertyOperation>> x = new HashMap<>();
//        for (PropertyOperation property : operations) {
//            List<PropertyOperation> properties = x.computeIfAbsent(property.did(), k -> new ArrayList<>());
//            properties.add(property);
//        }
//
//        List<Future<List<PropertyOperation>>> futures = new ArrayList<>();
//        x.forEach((did, properties) -> futures.add(getProperties(did, properties, traceId)));
//
//        FutureMerger.mergeList(futures)
//                .onComplete(ar -> {
//                    if (ar.succeeded()) {
//                        promise.complete(ar.result());
//                    } else {
//                        promise.fail(ar.cause());
//                    }
//                });
//
//        return promise.future();
//    }

//    public Future<List<PropertyOperation>> setProperties(String traceId, List<PropertyOperation> operations) {
//        Promise<List<PropertyOperation>> promise = Promise.promise();
//
//        Map<String, List<PropertyOperation>> x = new HashMap<>();
//
//        for (PropertyOperation property : operations) {
//            List<PropertyOperation> properties = x.computeIfAbsent(property.did(), k -> new ArrayList<>());
//            properties.add(property);
//        }
//
//        List<Future<List<PropertyOperation>>> futures = new ArrayList<>();
//        x.forEach((did, properties) -> futures.add(setProperties(did, properties, traceId)));
//
//        FutureMerger.mergeList(futures)
//                .onComplete(ar -> {
//                    if (ar.succeeded()) {
//                        promise.complete(ar.result());
//                    } else {
//                        promise.fail(ar.cause());
//                    }
//                });
//
//        return promise.future();
//    }

//    public Future<List<ActionOperation>> invokeActions(String traceId, List<ActionOperation> operations) {
//        Promise<List<ActionOperation>> promise = Promise.promise();
//
//        Map<String, List<ActionOperation>> x = new HashMap<>();
//        for (ActionOperation action : operations) {
//            List<ActionOperation> actions = x.computeIfAbsent(action.did(), k -> new ArrayList<>());
//            actions.add(action);
//        }
//
//        List<Future<List<ActionOperation>>> futures = new ArrayList<>();
//        x.forEach((did, actions) -> futures.add(invokeActions(did, actions, traceId)));
//
//        FutureMerger.mergeList(futures)
//                .onComplete(ar -> {
//                    if (ar.succeeded()) {
//                        promise.complete(ar.result());
//                    } else {
//                        promise.fail(ar.cause());
//                    }
//                });
//
//        return promise.future();
//    }

    public Future<IQ> getProperty(GetProperty.Query query) {
        return getProperty(query.property(), query.id()).map(query::result);
    }

    public Future<IQ> setProperty(SetProperty.Query query) {
        return setProperty(query.property(), query.id()).map(query::result);
    }

    public Future<IQ> invokeAction(InvokeAction.Query query) {
        return invokeAction(query.action(), query.id()).map(query::result);
    }

    public Future<List<PropertyOperation>> getProperties(String did, List<PropertyOperation> properties, String stanzaId) {
        Promise<List<PropertyOperation>> promise = Promise.promise();

        String endpointId = devices.get(did);
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.getProperties(stanzaId, properties)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                promise.complete(ar.result());
                            } else {
                                Throwable t = ar.cause();
                                if (t instanceof IotError e) {
                                    properties.forEach(x -> x.status(e.status()).description(e.description()));
                                } else {
                                    properties.forEach(x -> x.status(Status.INTERNAL_ERROR).description("getProperties failed: " + ar.cause().toString()));
                                }

                                promise.complete(properties);
                            }
                        });
            } else {
                properties.forEach(x -> x.status(Status.INTERNAL_ERROR).description("endpoint not found"));
                promise.complete(properties);
            }
        } else {
            JsonArray array = new JsonArray(devices.entrySet().stream()
                    .map(x -> new JsonObject().put("did", x.getKey()).put("endpointId", x.getValue()))
                    .collect(Collectors.toList()));

            logger.infov("{0} not found in: {1}", did, array.encode());

            properties.forEach(x -> x.status(Status.DEVICE_ID_NOT_EXIST).description("device not found"));
            promise.complete(properties);
        }

        return promise.future();
    }

    public Future<List<PropertyOperation>> setProperties(String did, List<PropertyOperation> properties, String stanzaId) {
        Promise<List<PropertyOperation>> promise = Promise.promise();

        String endpointId = devices.get(did);
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.setProperties(stanzaId, properties)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                promise.complete(ar.result());
                            } else {
                                Throwable t = ar.cause();
                                if (t instanceof IotError e) {
                                    properties.forEach(x -> x.status(e.status()).description(e.description()));
                                } else {
                                    properties.forEach(x -> x.status(Status.INTERNAL_ERROR).description("setProperties failed: " + ar.cause().toString()));
                                }

                                promise.complete(properties);
                            }
                        });
            } else {
                properties.forEach(x -> x.status(Status.INTERNAL_ERROR).description("endpoint not found"));
                promise.complete(properties);
            }
        } else {
            properties.forEach(x -> x.status(Status.DEVICE_ID_NOT_EXIST).description("device not found"));
            promise.complete(properties);
        }

        return promise.future();
    }

    public Future<List<ActionOperation>> invokeActions(String did, List<ActionOperation> actions, String stanzaId) {
        Promise<List<ActionOperation>> promise = Promise.promise();

        String endpointId = devices.get(did);
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.invokeActions(stanzaId, actions)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                promise.complete(ar.result());
                            } else {
                                Throwable t = ar.cause();
                                if (t instanceof IotError e) {
                                    actions.forEach(x -> x.status(e.status()).description(e.description()));
                                } else {
                                    actions.forEach(x -> x.status(Status.INTERNAL_ERROR).description("invokeActions failed: " + ar.cause().toString()));
                                }

                                promise.complete(actions);
                            }
                        });
            } else {
                actions.forEach(x -> x.status(Status.INTERNAL_ERROR).description("endpoint not found"));
                promise.complete(actions);
            }
        } else {
            actions.forEach(x -> x.status(Status.DEVICE_ID_NOT_EXIST).description("device not found"));
            promise.complete(actions);
        }

        return promise.future();
    }

    private Future<PropertyOperation> getProperty(PropertyOperation property, String stanzaId) {
        Promise<PropertyOperation> promise = Promise.promise();

        String endpointId = devices.get(property.did());
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.getProperty(stanzaId, property)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                promise.complete(ar.result());
                            } else {
                                Throwable t = ar.cause();
                                if (t instanceof IotError) {
                                    property.error((IotError) t);
                                } else {
                                    property.status(Status.INTERNAL_ERROR, "getProperty failed: " + ar.cause().toString());
                                }

                                promise.complete(property);
                            }
                        });
            } else {
                property.status(Status.INTERNAL_ERROR, "endpoint not found");
                promise.complete(property);
            }
        } else {
            JsonArray array = new JsonArray(devices.entrySet().stream()
                    .map(x -> new JsonObject().put("did", x.getKey()).put("endpointId", x.getValue()))
                    .collect(Collectors.toList()));
            logger.infov("{0} not found in: {1}", property.did(), array.encode());
            property.status(Status.DEVICE_ID_NOT_EXIST, "device not found");
            promise.complete(property);
        }

        return promise.future();
    }

    private Future<PropertyOperation> setProperty(PropertyOperation property, String stanzaId) {
        Promise<PropertyOperation> promise = Promise.promise();

        String endpointId = devices.get(property.did());
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.setProperty(stanzaId, property)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                promise.complete(ar.result());
                            } else {
                                Throwable t = ar.cause();
                                if (t instanceof IotError) {
                                    property.error((IotError) t);
                                } else {
                                    property.status(Status.INTERNAL_ERROR, "setProperty failed: " + ar.cause().toString());
                                }

                                promise.complete(property);
                            }
                        });
            } else {
                property.status(Status.INTERNAL_ERROR, "endpoint not found");
                promise.complete(property);
            }
        } else {
            JsonArray array = new JsonArray(devices.entrySet().stream()
                    .map(x -> new JsonObject().put("did", x.getKey()).put("endpointId", x.getValue()))
                    .collect(Collectors.toList()));
            logger.infov("{0} not found in: {1}", property.did(), array.encode());
            property.status(Status.DEVICE_ID_NOT_EXIST, "device not found");
            promise.complete(property);
        }

        return promise.future();
    }

    private Future<ActionOperation> invokeAction(ActionOperation action, String stanzaId) {
        Promise<ActionOperation> promise = Promise.promise();

        String endpointId = devices.get(action.did());
        if (endpointId != null) {
            XcpDeviceEndpoint endpoint = endpoints.get(endpointId);
            if (endpoint != null) {
                endpoint.invokeAction(stanzaId, action)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                promise.complete(ar.result());
                            } else {
                                Throwable t = ar.cause();
                                if (t instanceof IotError) {
                                    action.error((IotError) t);
                                } else {
                                    action.status(Status.INTERNAL_ERROR, "invokeAction failed: " + ar.cause().toString());
                                }

                                promise.complete(action);
                            }
                        });
            } else {
                action.status(Status.INTERNAL_ERROR, "endpoint not found");
                promise.complete(action);
            }
        } else {
            JsonArray array = new JsonArray(devices.entrySet().stream()
                    .map(x -> new JsonObject().put("did", x.getKey()).put("endpointId", x.getValue()))
                    .collect(Collectors.toList()));
            logger.infov("{0} not found in: {1}", action.did(), array.encode());
            action.status(Status.DEVICE_ID_NOT_EXIST).description("device not found");
            promise.complete(action);
        }

        return promise.future();
    }
}

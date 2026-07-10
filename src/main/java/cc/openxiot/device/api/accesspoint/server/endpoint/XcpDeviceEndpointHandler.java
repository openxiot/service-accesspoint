package cc.openxiot.device.api.accesspoint.server.endpoint;

import cc.openxiot.controller.db.ownership.Ownership;
import cc.openxiot.controller.db.ownership.OwnershipId;
import cc.openxiot.controller.db.ownership.OwnershipRepository;
import cc.openxiot.device.api.accesspoint.server.endpoint.event.DeviceEventService;
import cc.openxiot.device.db.registry.DeviceRegistry;
import cc.openxiot.device.db.registry.DeviceRegistryMapper;
import cc.openxiot.device.db.registry.DeviceRegistryRepository;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.notice.device.impl.*;
import cn.geekcity.xiot.spec.operation.EventOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import cn.geekcity.xiot.spec.ownership.DeviceOwner;
import cn.geekcity.xiot.spec.status.Status;
import cn.geekcity.xiot.spec.summary.Summary;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class XcpDeviceEndpointHandler {

    @Inject
    Logger logger;

    @Inject
    DeviceRegistryRepository registry;

    @Inject
    DeviceEventService event;

    @Inject
    OwnershipRepository ownership;

    //------------------------------------------------------------------------------
    // 需要推送到 事件处理中心
    //------------------------------------------------------------------------------

    public Uni<Void> onActive(XcpDeviceEndpoint endpoint) {
        logger.infov("onActive: {0}", endpoint.root().did());

        DeviceRegistry device = DeviceRegistryMapper.toEntity(endpoint.root(), endpoint.replicaIp());

        return registry.register(device)
                .invoke(v -> this.event.publish(new DeviceRootActive(device.did)))
                .chain(v -> onSummaryChanged(endpoint, endpoint.root().did(), endpoint.root().summary().online(true), "active"));
    }

    public Uni<Void> onInactive(XcpDeviceEndpoint endpoint) {
        logger.infov("onInactive: {0}", endpoint.root().did());

        return registry.update(endpoint.root().did(), false)
                .invoke(v -> this.event.publish(new DeviceRootInactive(endpoint.root().did())))
                .chain(v -> onSummaryChanged(endpoint, endpoint.root().did(), endpoint.root().summary().online(false), "active"));
    }

    public Uni<Void> onChildrenActive(XcpDeviceEndpoint endpoint, List<DeviceImage> children) {
        Uni<DeviceRegistry> uniRoot = registry.get(endpoint.root().did());
        Uni<List<DeviceRegistry>> uniLastChildren = registry.getChildren(endpoint.root().did());
        Uni<List<DeviceRegistry>> uniRegistered = registry.get(children.stream().map(DeviceImage::did).toList());

        return Uni.combine().all()
                .unis(uniRoot, uniLastChildren, uniRegistered)
                .with((root, lastChildren, registeredList) -> {
                    Map<String, DeviceRegistry> registered = registeredList.stream()
                            .collect(Collectors.toMap(x -> x.did, Function.identity()));

                    List<DeviceRegistry> added = new ArrayList<>();
                    List<DeviceRegistry> changed = new ArrayList<>();
                    List<DeviceRegistry> removed = new ArrayList<>();

                    for (DeviceImage child : children) {
                        DeviceRegistry device = registered.get(child.did());
                        if (device == null) {
                            added.add(DeviceRegistry.of(child.did(), child.summary(), root));
                        } else {
                            if (device.change(child.summary(), root)) {
                                changed.add(device);
                            }
                        }
                    }

                    Map<String, DeviceImage> current = children.stream()
                            .collect(Collectors.toMap(DeviceImage::did, Function.identity()));
                    for (DeviceRegistry last : lastChildren) {
                        last.rootId = null;
                        if (!current.containsKey(last.did)) {
                            removed.add(last);
                        }
                    }

                    logger.infov("added: {0}, changed: {1}, removed: {2}", added.size(), changed.size(), removed.size());

                    return new ChildrenResult(root.did, added, changed, removed, children);
                })
                .chain(r -> registry.register(r.added)
                        .chain(v -> registry.update(r.removed))
                        .chain(v -> registry.update(r.changed))
                        .invoke(v -> {
                            publishAdded(r.rootId, r.added, r.children);
                            publishRemoved(r.rootId, r.removed, r.children);
                            publishChanged(r.rootId, r.changed, r.children);
                        }));
    }

    public Uni<Void> onChildrenAdded(XcpDeviceEndpoint endpoint, List<DeviceImage> children) {
        logger.infov("onChildrenAdded");

        return registry.get(endpoint.root().did())
                .chain(root -> {
                    List<DeviceRegistry> devices = DeviceRegistryMapper.toEntities(children, endpoint.replicaIp());
                    for (DeviceRegistry device : devices) {
                        device.rootId = root.rootId;
                        device.accessKey = root.accessKey;
                    }
                    return registry.register(devices)
                            .invoke(v -> {
                                List<Device> list = children.stream()
                                        .map(x -> new Device(x.did(), x.summary()))
                                        .toList();
                                event.publish(new DeviceChildrenAdded(root.did).children(list));
                            });
                });
    }

    public Uni<Void> onChildrenRemoved(XcpDeviceEndpoint endpoint, List<DeviceImage> children) {
        logger.infov("onChildrenRemoved");

        return registry.get(endpoint.root().did())
                .chain(root -> {
                    List<DeviceRegistry> devices = children.stream()
                            .map(child -> DeviceRegistry.of(child.did(), child.summary(), root))
                            .toList();
                    return registry.register(devices)
                            .invoke(v -> {
                                List<String> list = children.stream().map(DeviceImage::did).toList();
                                event.publish(new DeviceChildrenRemoved(root.did).children(list));
                            });
                });
    }

    public Uni<Void> onPropertiesChanged(XcpDeviceEndpoint endpoint, List<PropertyOperation> properties, String id) {
        logger.infov("onPropertiesChanged: {0}", id);

        if (properties.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        this.event.publish(new DevicePropertiesChanged(new Date().getTime(), properties));
        return Uni.createFrom().voidItem();
    }

    public Uni<Void> onEventOccurred(XcpDeviceEndpoint endpoint, EventOperation eventOp, String id) {
        logger.infov("onEventOccurred: {0}", id);

        this.event.publish(new DeviceEventOccurred(new Date().getTime(), eventOp));
        return Uni.createFrom().voidItem();
    }

    public Uni<Void> onSummaryChanged(XcpDeviceEndpoint endpoint, String did, Summary summary, String id) {
        logger.infov("onSummaryChanged: {0}", id);

        return registry.updateOneWithoutAccessKey(did, summary)
                .invoke(v -> this.event.publish(new DeviceSummaryChanged(did).summary(summary)));
    }

    public void onAccessKeyChanged(XcpDeviceEndpoint endpoint, String did, String key, String id) {
        logger.infov("onAccessKeyChanged: {0} => {1}", did, key);

        this.event.publish(new DeviceAccessKeyChanged(did, key));
    }

    private void publishAdded(String rootId, List<DeviceRegistry> added, List<DeviceImage> children) {
        if (added.isEmpty()) return;

        List<String> devices = added.stream().map(x -> x.did).toList();
        List<Device> childrenAdded = children.stream()
                .filter(x -> devices.contains(x.did()))
                .map(x -> new Device(x.did(), x.summary()))
                .toList();

        event.publish(new DeviceChildrenAdded(rootId).children(childrenAdded));
    }

    private void publishRemoved(String rootId, List<DeviceRegistry> removed, List<DeviceImage> children) {
        if (removed.isEmpty()) return;

        List<String> devices = removed.stream().map(x -> x.did).toList();
        event.publish(new DeviceChildrenRemoved(rootId).children(devices));
    }

    private void publishChanged(String rootId, List<DeviceRegistry> changed, List<DeviceImage> children) {
        if (changed.isEmpty()) return;

        List<String> devices = changed.stream().map(x -> x.did).toList();
        for (DeviceImage child : children) {
            if (devices.contains(child.did())) {
                event.publish(new DeviceSummaryChanged(child.did()).summary(child.summary()));
            }
        }
    }

    //------------------------------------------------------------------------------
    // AccessKey 读写
    //------------------------------------------------------------------------------

    public Uni<Void> setAccessKey(String did, String key) {
        logger.infov("setAccessKey: {0} => {1}", did, key);
        return registry.updateAccessKey(did, key);
    }

    public Uni<String> getAccessKey(String did) {
        logger.infov("getAccessKey: {0}", did);
        return registry.get(did).chain(device -> {
            if (device == null) {
                return Uni.createFrom().failure(new IotError(Status.INTERNAL_ERROR, "device not found"));
            }
            return Uni.createFrom().item(device.accessKey);
        });
    }

    //------------------------------------------------------------------------------
    // Owner 读写
    //------------------------------------------------------------------------------

    public Uni<Void> addOwner(String did, DeviceOwner owner) {
        logger.infov("addOwner: {0} => {1}/{2}", did, owner.appId(), owner.ownerId());

        return registry.get(did).chain(device -> {
            if (device == null) {
                return Uni.createFrom().failure(new IotError(Status.INTERNAL_ERROR, "device not found"));
            }
            return ownership.add(Ownership.of(did, owner.appId(), owner.ownerId(), device.accessKey));
        });
    }

    public Uni<Void> removeOwner(String did, DeviceOwner owner) {
        logger.infov("removeOwner: {0} => {1}/{2}", did, owner.appId(), owner.ownerId());

        OwnershipId id = new OwnershipId(did, owner.appId(), owner.ownerId());
        return ownership.remove(id);
    }

    public Uni<List<DeviceOwner>> getOwners(String did) {
        logger.infov("getOwners: {0}", did);

        return ownership.get(did)
                .map(ownerships -> ownerships.stream()
                        .map(x -> new DeviceOwner(x.appId, x.ownerId))
                        .toList());
    }

    private record ChildrenResult(
            String rootId,
            List<DeviceRegistry> added,
            List<DeviceRegistry> removed,
            List<DeviceRegistry> changed,
            List<DeviceImage> children
    ) {
    }
}

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

    public void onActive(XcpDeviceEndpoint endpoint) {
        logger.infov("onActive: {0}", endpoint.root().did());

        DeviceRegistry device = DeviceRegistryMapper.toEntity(endpoint.root(), endpoint.replicaIp());
        registry.register(device);

        this.event.publish(new DeviceRootActive(device.did));

        onSummaryChanged(endpoint, endpoint.root().did(), endpoint.root().summary().online(true), "active");
//        onPropertiesChanged(endpoint,  endpoint.root().getLastPropertyOperations(), "active");
    }

    public void onInactive(XcpDeviceEndpoint endpoint) {
        logger.infov("onInactive: {0}", endpoint.root().did());

        registry.update(endpoint.root().did(), false);

        this.event.publish(new DeviceRootInactive(endpoint.root().did()));

        onSummaryChanged(endpoint, endpoint.root().did(), endpoint.root().summary().online(false), "active");
    }

    public void onChildrenActive(XcpDeviceEndpoint endpoint, List<DeviceImage> children) {
        DeviceRegistry root = registry.get(endpoint.root().did());
        List<DeviceRegistry> lastChildren = registry.getChildren(endpoint.root().did());
        Map<String, DeviceRegistry> registered = registry.get(children.stream().map(DeviceImage::did).toList())
                .stream().collect(Collectors.toMap(x -> x.did, Function.identity()));

        List<DeviceRegistry> added = new ArrayList<>();
        List<DeviceRegistry> changed = new ArrayList<>();
        List<DeviceRegistry> removed = new ArrayList<>();

        // 计算哪些是需要添加的，哪些是需要修改的
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

        // 计算哪些是需要删除的
        Map<String, DeviceImage> current = children.stream().collect(Collectors.toMap(DeviceImage::did, Function.identity()));
        for (DeviceRegistry last : lastChildren) {
            last.rootId = null;

            if (!current.containsKey(last.did)) {
                removed.add(last);
            }
        }

        logger.infov("added: {0}, changed: {1}, removed: {2}", added.size(), changed.size(), removed.size());

        registry.register(added);
        registry.update(removed);
        registry.update(changed);

        publishAdded(root.did, added, children);
        publishRemoved(root.did, removed, children);
        publishChanged(root.did, changed, children);
    }

    public void onChildrenAdded(XcpDeviceEndpoint endpoint, List<DeviceImage> children) {
        logger.infov("onChildrenAdded");

        DeviceRegistry root = registry.get(endpoint.root().did());

        List<DeviceRegistry> devices = DeviceRegistryMapper.toEntities(children, endpoint.replicaIp());
        for (DeviceRegistry device : devices) {
            device.rootId = root.rootId;
            device.accessKey = root.accessKey;
        }

        registry.register(devices);

        List<Device> list = children.stream().map(x -> new Device(x.did(), x.summary())).collect(Collectors.toList());
        DeviceChildrenAdded childrenAdded = new DeviceChildrenAdded(root.did).children(list);
        event.publish(childrenAdded);
    }

    public void onChildrenRemoved(XcpDeviceEndpoint endpoint, List<DeviceImage> children) {
        logger.infov("onChildrenRemoved");

        DeviceRegistry root = registry.get(endpoint.root().did());

        List<DeviceRegistry> devices = children.stream()
                .map(child -> DeviceRegistry.of(child.did(), child.summary(), root))
                .collect(Collectors.toList());

        registry.register(devices);

        List<String> list = children.stream().map(DeviceImage::did).collect(Collectors.toList());
        DeviceChildrenRemoved childrenRemoved = new DeviceChildrenRemoved(root.did).children(list);
        event.publish(childrenRemoved);
    }

    public void onPropertiesChanged(XcpDeviceEndpoint endpoint, List<PropertyOperation> properties, String id) {
        logger.infov("onPropertiesChanged: {0}", id);

        if (properties.isEmpty()) {
            return;
        }

        this.event.publish(new DevicePropertiesChanged(new Date().getTime(), properties));
    }

    public void onEventOccurred(XcpDeviceEndpoint endpoint, EventOperation event, String id) {
        logger.infov("onEventOccurred: {0}", id);

        this.event.publish(new DeviceEventOccurred(new Date().getTime(), event));
    }

    public void onSummaryChanged(XcpDeviceEndpoint endpoint, String did, Summary summary, String id) {
        logger.infov("onSummaryChanged: {0}", id);

        registry.updateOneWithoutAccessKey(did, summary);

        this.event.publish(new DeviceSummaryChanged(did).summary(summary));
    }

    public void onAccessKeyChanged(XcpDeviceEndpoint endpoint, String did, String key, String id) {
        logger.infov("onAccessKeyChanged: {0} => {1}", did, key);

        this.event.publish(new DeviceAccessKeyChanged(did, key));
    }

    private void publishAdded(String rootId, List<DeviceRegistry> added, List<DeviceImage> children) {
        if (added.isEmpty()) {
            return;
        }

        List<String> devices = added.stream().map(x -> x.did).toList();

        List<Device> childrenAdded = children.stream()
                .filter(x -> devices.contains(x.did()))
                .map(x -> new Device(x.did(), x.summary()))
                .toList();

        event.publish(new DeviceChildrenAdded(rootId).children(childrenAdded));
    }

    private void publishRemoved(String rootId, List<DeviceRegistry> removed, List<DeviceImage> children) {
        if (removed.isEmpty()) {
            return;
        }

        List<String> devices = removed.stream().map(x -> x.did).toList();
        event.publish(new DeviceChildrenRemoved(rootId).children(devices));
    }

    private void publishChanged(String rootId, List<DeviceRegistry> changed, List<DeviceImage> children) {
        if (changed.isEmpty()) {
            return;
        }

        List<String> devices = changed.stream().map(x -> x.did).toList();

        for (DeviceImage child : children) {
            if (devices.contains(child.did())) {
                DeviceSummaryChanged summaryChanged = new DeviceSummaryChanged(child.did()).summary(child.summary());
                event.publish(summaryChanged);
            }
        }
    }

    //------------------------------------------------------------------------------
    // AccessKey 读写
    //------------------------------------------------------------------------------

    public void setAccessKey(String did, String key) {
        logger.infov("setAccessKey: {0} => {1}", did, key);
        registry.updateAccessKey(did, key);
    }

    public String getAccessKey(String did) throws IotError {
        logger.infov("getAccessKey: {0}", did);
        DeviceRegistry device = registry.get(did);
        if (device == null) {
            throw new IotError(Status.INTERNAL_ERROR, "device not found");
        }

        return device.accessKey;
    }

    //------------------------------------------------------------------------------
    // Owner 读写
    //------------------------------------------------------------------------------

    public void addOwner(String did, DeviceOwner owner) throws IotError {
        logger.infov("addOwner: {0} => {1}/{2}", did, owner.appId(), owner.ownerId());

        DeviceRegistry device = registry.get(did);
        if (device == null) {
            throw new IotError(Status.INTERNAL_ERROR, "device not found");
        }

        ownership.add(Ownership.of(did, owner.appId(), owner.ownerId(), device.accessKey));
    }

    public void removeOwner(String did, DeviceOwner owner) {
        logger.infov("removeOwner: {0} => {1}/{2}", did, owner.appId(), owner.ownerId());

        OwnershipId id = new OwnershipId(did, owner.appId(), owner.ownerId());
        ownership.remove(id);
    }

    public List<DeviceOwner> getOwners(String did) {
        logger.infov("getOwners: {0}", did);

        List<Ownership> ownerships = ownership.get(did);
        return ownerships.stream().map(x -> new DeviceOwner(x.appId, x.ownerId)).toList();
    }
}

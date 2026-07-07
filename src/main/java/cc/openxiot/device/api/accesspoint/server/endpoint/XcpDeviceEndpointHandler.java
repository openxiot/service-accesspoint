package cc.openxiot.device.api.accesspoint.server.endpoint;

import cc.openxiot.controller.db.ownership.Ownership;
import cc.openxiot.controller.db.ownership.OwnershipId;
import cc.openxiot.controller.db.ownership.OwnershipRepository;
import cc.openxiot.device.api.accesspoint.server.endpoint.event.DeviceEventService;
import cc.openxiot.device.db.registry.DeviceRegistry;
import cc.openxiot.device.db.registry.DeviceRegistryMapper;
import cc.openxiot.device.db.registry.DeviceRegistryRepository;
import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.spec.notice.device.impl.DeviceRootActive;
import cn.geekcity.xiot.spec.notice.device.impl.DeviceRootInactive;
import cn.geekcity.xiot.spec.operation.EventOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import cn.geekcity.xiot.spec.ownership.DeviceOwner;
import cn.geekcity.xiot.spec.status.Status;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

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

    public void onActive(XcpDeviceEndpoint endpoint) {
        logger.infov("onActive: {0}", endpoint.root().did());

        DeviceRegistry device = DeviceRegistryMapper.toEntity(endpoint.root(), endpoint.replicaIp());

        registry.register(device);

        event.publish(new DeviceRootActive(device.did));
    }

    public void onInactive(XcpDeviceEndpoint endpoint) {
        logger.infov("onInactive: {0}", endpoint.root().did());

        registry.update(endpoint.root().did(), false);

        event.publish(new DeviceRootInactive(endpoint.root().did()));
    }

    public void onPropertiesChanged(XcpDeviceEndpoint endpoint, List<PropertyOperation> properties, String id) {
        logger.infov("onPropertiesChanged: {0}", id);
    }

    public void onEventOccurred(XcpDeviceEndpoint endpoint, EventOperation event, String id) {
        logger.infov("onEventOccurred: {0}", id);
    }

    public void onAccessKeyChanged(XcpDeviceEndpoint endpoint, String did, String key, String id) {
        logger.infov("onAccessKeyChanged: {0} => {1}", did, key);
    }

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

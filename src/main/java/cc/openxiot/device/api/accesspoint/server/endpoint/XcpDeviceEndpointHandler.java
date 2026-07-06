package cc.openxiot.device.api.accesspoint.server.endpoint;

import cc.openxiot.device.api.accesspoint.server.endpoint.event.DeviceEventService;
import cc.openxiot.device.db.registry.DeviceRegistry;
import cc.openxiot.device.db.registry.DeviceRegistryMapper;
import cc.openxiot.device.db.registry.DeviceRegistryRepository;
import cn.geekcity.xiot.spec.notice.device.impl.DeviceRootActive;
import cn.geekcity.xiot.spec.notice.device.impl.DeviceRootInactive;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class XcpDeviceEndpointHandler {

    @Inject
    Logger logger;

    @Inject
    DeviceRegistryRepository registry;

    @Inject
    DeviceEventService event;

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
}

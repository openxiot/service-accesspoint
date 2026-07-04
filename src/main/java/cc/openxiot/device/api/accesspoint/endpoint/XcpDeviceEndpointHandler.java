package cc.openxiot.device.api.accesspoint.endpoint;

import cc.openxiot.device.db.registry.DeviceRegistry;
import cc.openxiot.device.db.registry.DeviceRegistryMapper;
import cc.openxiot.device.db.registry.DeviceRegistryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class XcpDeviceEndpointHandler {

    @Inject
    Logger logger;

    @Inject
    DeviceRegistryRepository registry;

    public void onActive(XcpDeviceEndpoint endpoint) {
        logger.infov("onActive: {0}", endpoint.root().did());

        DeviceRegistry device = DeviceRegistryMapper.toEntity(endpoint.root(), endpoint.replicaIp());

        registry.register(device);
    }

    public void onInactive(XcpDeviceEndpoint endpoint) {
        logger.infov("onInactive: {0}", endpoint.root().did());

        registry.update(endpoint.root().did(), false);
    }
}

package cc.openxiot.device.db.registry;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DeviceRegistryRepository implements PanacheMongoRepositoryBase<DeviceRegistry, String> {

    public void register(DeviceRegistry device) {
        prepareRegistration(device, get(device.did));
        persistOrUpdate(device);
    }

    public void register(Collection<DeviceRegistry> devices) {
        if (devices.isEmpty()) {
            return;
        }

        List<String> list = devices.stream()
                .map(d -> d.did)
                .collect(Collectors.toList());

        Map<String, DeviceRegistry> existed = get(list).stream().collect(Collectors.toMap(d -> d.did, d -> d));

        for (DeviceRegistry device : devices) {
            prepareRegistration(device, existed.get(device.did));
        }

        persistOrUpdate(devices);
    }

    public void update(String did, boolean online) {
        DeviceRegistry device = get(did);
        if (device != null) {
            device.online = online;

            if (online) {
                device.lastOnline = new Date();
            } else {
                device.lastOffline = new Date();
            }

            update(device);
        }
    }

    public void update(DeviceRegistry device) {
        persistOrUpdate(device);
    }

    public void update(Collection<DeviceRegistry> devices) {
        persistOrUpdate(devices);
    }

    public void updateAccessKey(String did, String key) {
        DeviceRegistry entity = findById(did);
        if (entity != null) {
            entity.accessKey = key;
            update(entity);
        }
    }

    public void updateAccessPoint(String did, String accesspoint) {
        DeviceRegistry entity = findById(did);
        if (entity != null) {
            entity.accessPoint = accesspoint;
            update(entity);
        }
    }

    public DeviceRegistry get(String did) {
        return findById(did);
    }

    public List<DeviceRegistry> get(List<String> dids) {
        return list("did in ?1", dids);
    }

    public List<DeviceRegistry> getChildren(String rootId) {
        return list("rootId", rootId);
    }

    private void prepareRegistration(DeviceRegistry device, DeviceRegistry existed) {
        if (existed != null) {
            device.accessKey = existed.accessKey;
            device.create = existed.create;
        } else {
            device.accessKey = UUID.randomUUID().toString();
            device.create = new Date();
        }
        device.online = true;
        device.lastOnline = new Date();
    }
}

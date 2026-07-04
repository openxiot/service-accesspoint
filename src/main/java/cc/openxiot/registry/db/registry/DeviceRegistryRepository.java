package cc.openxiot.registry.db.registry;

import cn.geekcity.xiot.spec.device.Device;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class DeviceRegistryRepository implements PanacheMongoRepositoryBase<DeviceRegistry, String> {

    public void register(Device device) {
        persistOrUpdate(DeviceRegistryMapper.toEntity(device));
    }

    public void register(Collection<Device> devices) {
        List<DeviceRegistry> entities = devices.stream()
                .map(DeviceRegistryMapper::toEntity)
                .collect(Collectors.toList());
        persistOrUpdate(entities);
    }

    public void add(Device device) {
        if (findById(device.did()) == null) {
            persist(DeviceRegistryMapper.toEntity(device));
        }
    }

    public void add(Collection<Device> devices) {
        for (Device device : devices) {
            add(device);
        }
    }

    public void remove(String did) {
        deleteById(did);
    }

    public void remove(List<String> dids) {
        delete("did in ?1", dids);
    }

    public void update(Device device) {
        persistOrUpdate(DeviceRegistryMapper.toEntity(device));
    }

    public void updateWithoutAccessKey(Device device) {
        DeviceRegistry entity = findById(device.did());
        if (entity == null) return;
        DeviceRegistryMapper.mergeSummary(entity, device.summary());
        update(entity);
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

    public void update(Collection<Device> devices) {
        List<DeviceRegistry> entities = devices.stream()
                .map(DeviceRegistryMapper::toEntity)
                .collect(Collectors.toList());
        persistOrUpdate(entities);
    }

    public Device get(String did) {
        DeviceRegistry entity = findById(did);
        return entity != null ? DeviceRegistryMapper.toDevice(entity) : null;
    }

    public List<Device> get(List<String> dids) {
        return list("did in ?1", dids).stream()
                .map(DeviceRegistryMapper::toDevice)
                .collect(Collectors.toList());
    }

    public List<Device> getChildren(String rootId) {
        return list("rootId", rootId).stream()
                .map(DeviceRegistryMapper::toDevice)
                .collect(Collectors.toList());
    }
}

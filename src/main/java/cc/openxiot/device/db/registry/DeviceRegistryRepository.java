package cc.openxiot.device.db.registry;

import cn.geekcity.xiot.spec.summary.Summary;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DeviceRegistryRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<DeviceRegistry> getCollection() {
        return mongoClient.getDatabase("device").getCollection("registry", DeviceRegistry.class);
    }

    public Uni<Void> register(DeviceRegistry device) {
        return get(device.did)
                .chain(existed -> {
                    prepareRegistration(device, existed);
                    return upsert(device);
                });
    }

    public Uni<Void> register(Collection<DeviceRegistry> devices) {
        if (devices.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<String> dids = devices.stream().map(d -> d.did).collect(Collectors.toList());

        return get(dids)
                .chain(existedList -> {
                    Map<String, DeviceRegistry> existed = existedList.stream()
                            .collect(Collectors.toMap(d -> d.did, d -> d));
                    for (DeviceRegistry device : devices) {
                        prepareRegistration(device, existed.get(device.did));
                    }
                    return upsert(devices);
                });
    }

    public Uni<Void> update(String did, boolean online) {
        return get(did).chain(device -> {
            if (device != null) {
                device.online = online;
                if (online) {
                    device.lastOnline = new Date();
                } else {
                    device.lastOffline = new Date();
                }
                return upsert(device);
            }
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<Void> update(DeviceRegistry device) {
        return upsert(device);
    }

    public Uni<Void> update(Collection<DeviceRegistry> devices) {
        if (devices.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return upsert(devices);
    }

    public Uni<Void> updateOneWithoutAccessKey(String did, Summary summary) {
        return get(did).chain(entity -> {
            if (entity != null) {
                entity.type = summary.type().toString();
                entity.parentId = summary.parentId();
                entity.online = summary.online();
                entity.protocol = summary.protocol();
                entity.members = summary.members();
                return upsert(entity);
            }
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<Void> updateAccessKey(String did, String key) {
        return get(did).chain(entity -> {
            if (entity != null) {
                entity.accessKey = key;
                return upsert(entity);
            }
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<Void> updateAccessPoint(String did, String accesspoint) {
        return get(did).chain(entity -> {
            if (entity != null) {
                entity.accessPoint = accesspoint;
                return upsert(entity);
            }
            return Uni.createFrom().voidItem();
        });
    }

    public Uni<DeviceRegistry> get(String did) {
        return getCollection().find(Filters.eq("_id", did)).collect().first();
    }

    public Uni<List<DeviceRegistry>> get(List<String> dids) {
        if (dids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return getCollection().find(Filters.in("did", dids)).collect().asList();
    }

    public Uni<List<DeviceRegistry>> getChildren(String rootId) {
        return getCollection().find(Filters.eq("rootId", rootId)).collect().asList();
    }

    private Uni<Void> upsert(DeviceRegistry device) {
        return getCollection()
                .replaceOne(Filters.eq("_id", device.did), device, new ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    private Uni<Void> upsert(Collection<DeviceRegistry> devices) {
        return Uni.combine().all().unis(
                devices.stream().map(this::upsert).toList()
        ).discardItems();
    }

    private void prepareRegistration(DeviceRegistry device, DeviceRegistry existed) {
        if (existed != null) {
            device.accessKey = existed.accessKey;
            device.create = existed.create;
        } else {
            device.accessKey = UUID.randomUUID().toString().toUpperCase().replace("-", "");
            device.create = new Date();
        }
        device.online = true;
        device.lastOnline = new Date();
    }
}

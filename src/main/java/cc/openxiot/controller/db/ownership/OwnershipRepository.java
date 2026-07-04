package cc.openxiot.controller.db.ownership;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class OwnershipRepository implements PanacheMongoRepositoryBase<Ownership, OwnershipKey> {

    public void add(Ownership ownership) {
        ensureKey(ownership);
        persist(ownership);
    }

    public void add(List<Ownership> ownerships) {
        ownerships.forEach(this::ensureKey);
        persist(ownerships);
    }

    public void remove(List<Ownership> ownerships) {
        List<OwnershipKey> ids = ownerships.stream()
                .map(o -> o.id)
                .collect(Collectors.toList());
        delete("_id in ?1", ids);
    }

    public void update(Ownership ownership) {
        ensureKey(ownership);
        persistOrUpdate(ownership);
    }

    public void update(Collection<Ownership> ownerships) {
        ownerships.forEach(this::ensureKey);
        for (Ownership o : ownerships) {
            persistOrUpdate(o);
        }
    }

    public Ownership get(String did, String appId, String ownerId) {
        return findById(new OwnershipKey(did, appId, ownerId));
    }

    public List<Ownership> get(String appId, String ownerId, List<String> deviceIds) {
        return list("did in ?1 and appId = ?2 and ownerId = ?3", deviceIds, appId, ownerId);
    }

    public List<Ownership> get(String appId, String ownerId) {
        return list("appId = ?1 and ownerId = ?2", appId, ownerId);
    }

    public List<Ownership> get(String did) {
        return list("did", did);
    }

    private void ensureKey(Ownership o) {
        if (o.id == null) {
            o.id = new OwnershipKey(o.did, o.appId, o.ownerId);
        }
    }
}

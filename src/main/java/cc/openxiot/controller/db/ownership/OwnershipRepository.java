package cc.openxiot.controller.db.ownership;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class OwnershipRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "default")
    String database;

    private ReactiveMongoCollection<Ownership> getCollection() {
        return mongoClient.getDatabase(database).getCollection("ownership", Ownership.class);
    }

    public Uni<Void> add(Ownership ownership) {
        ensureKey(ownership);
        return getCollection().insertOne(ownership).replaceWithVoid();
    }

    public Uni<Void> add(List<Ownership> ownerships) {
        ownerships.forEach(this::ensureKey);
        return getCollection().insertMany(ownerships).replaceWithVoid();
    }

    public Uni<Void> remove(OwnershipId id) {
        return getCollection()
                .deleteOne(Filters.eq("_id", id))
                .replaceWithVoid();
    }

    public Uni<Void> remove(List<Ownership> ownerships) {
        List<OwnershipId> ids = ownerships.stream().map(o -> o.id).toList();
        return getCollection()
                .deleteMany(Filters.in("_id", ids))
                .replaceWithVoid();
    }

    public Uni<Void> update(Ownership ownership) {
        ensureKey(ownership);
        return getCollection()
                .replaceOne(Filters.eq("_id", ownership.id), ownership, new ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    public Uni<Void> update(Collection<Ownership> ownerships) {
        ownerships.forEach(this::ensureKey);
        return Uni.combine().all().unis(
                ownerships.stream().map(o ->
                        getCollection()
                                .replaceOne(Filters.eq("_id", o.id), o, new ReplaceOptions().upsert(true))
                                .replaceWithVoid()
                ).toList()
        ).discardItems();
    }

    public Uni<Ownership> get(String did, String appId, String ownerId) {
        return getCollection()
                .find(Filters.eq("_id", new OwnershipId(did, appId, ownerId)))
                .collect().first();
    }

    public Uni<List<Ownership>> get(String appId, String ownerId, List<String> deviceIds) {
        return getCollection()
                .find(Filters.and(
                        Filters.in("did", deviceIds),
                        Filters.eq("appId", appId),
                        Filters.eq("ownerId", ownerId)
                ))
                .collect().asList();
    }

    public Uni<List<Ownership>> get(String appId, String ownerId) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("appId", appId),
                        Filters.eq("ownerId", ownerId)
                ))
                .collect().asList();
    }

    public Uni<List<Ownership>> get(String did) {
        return getCollection()
                .find(Filters.eq("did", did))
                .collect().asList();
    }

    private void ensureKey(Ownership o) {
        if (o.id == null) {
            o.id = new OwnershipId(o.did, o.appId, o.ownerId);
        }
    }
}

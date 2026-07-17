package cc.openxiot.device.api.accesspoint.server.endpoint.console;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ConsoleService {

    @Inject
    Logger logger;

    @Inject
    @RestClient
    ConsoleClient client;

    public Uni<JsonObject> applyOne(String orgId, String signature, String fingerprint, boolean cert) {
        return client.applyOne(orgId, signature, fingerprint, cert)
                .map(JsonObject::new);
    }

    public Uni<JsonObject> applyOne(String orgId, String signature, String fingerprint) {
        return client.applyOne(orgId, signature, fingerprint, false)
                .map(JsonObject::new);
    }

    public Uni<JsonObject> applyMany(String orgId, String signature, List<String> fingerprints, boolean cert) {
        return applyMany(orgId, signature, fingerprints, cert);
    }

    public Uni<JsonObject> applyMany(String orgId, String signature, List<String> fingerprints) {
        return client.applyMany(orgId, signature, fingerprints, false)
                .map(JsonObject::new);
    }

    public Uni<Boolean> probeOne(String did) {
        return client.probeOne(did)
                .map(Boolean::parseBoolean);
    }

    public Uni<Set<String>> probeMany(List<String> dids) {
        return client.probeMany(dids)
                .map(response -> {
                    Set<String> set = new HashSet<>();
                    JsonObject o = new JsonObject(response);
                    for (String id : dids) {
                        if (o.getBoolean(id, false)) {
                            set.add(id);
                        }
                    }
                    return set;
                });
    }
}

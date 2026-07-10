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

    public void applyOne(String orgId, String key) {
        client.applyOne(orgId, key, false);
    }

    public void applyMany(String orgId, List<String> keys) {
        client.applyMany(orgId, keys, false);
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

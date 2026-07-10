package cc.openxiot.device.api.accesspoint.server.endpoint.console;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public boolean probeOne(String did) {
        String response = client.probeOne(did);
        return Boolean.parseBoolean(response);
    }

    public Set<String> probeMany(List<String> did) {
        Set<String> set = new HashSet<>();

        String response = client.probeMany(did);
        JsonObject o = new JsonObject(response);
        for (String id : did) {
            boolean value = o.getBoolean("id", false);
            if (value) {
                set.add(id);
            }
        }

        return set;
    }
}

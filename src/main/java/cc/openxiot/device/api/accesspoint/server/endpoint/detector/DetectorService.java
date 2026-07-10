package cc.openxiot.device.api.accesspoint.server.endpoint.detector;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DetectorService {

    @Inject
    Logger logger;

    public Uni<Boolean> probe(String did, String accessPoint) {
        if (accessPoint == null || accessPoint.isBlank()) {
            logger.warnv("probe skipped: accessPoint is empty, did={0}", did);
            return Uni.createFrom().item(false);
        }

        try {
            DetectorClient client = RestClientBuilder.newBuilder()
                    .baseUri(URI.create("http://" + accessPoint))
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(DetectorClient.class);

            return client.probe(did)
                    .map(Boolean::parseBoolean)
                    .onFailure().recoverWithItem(e -> {
                        logger.warnv("probe failed: did={0}, accessPoint={1}, error={2}", did, accessPoint, e.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            logger.warnv("probe failed: did={0}, accessPoint={1}, error={2}", did, accessPoint, e.getMessage());
            return Uni.createFrom().item(false);
        }
    }
}

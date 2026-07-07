package cc.openxiot.device.api.accesspoint.server.endpoint.detector;

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

    public boolean probe(String did, String accessPoint) {
        if (accessPoint == null || accessPoint.isBlank()) {
            logger.warnv("probe skipped: accessPoint is empty, did={0}", did);
            return false;
        }

        try {
            DetectorClient client = RestClientBuilder.newBuilder()
                    .baseUri(URI.create("http://" + accessPoint))
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(DetectorClient.class);

            String response = client.probe(did);
            return Boolean.parseBoolean(response);
        } catch (Exception e) {
            logger.warnv("probe failed: did={0}, accessPoint={1}, error={2}", did, accessPoint, e.getMessage());
            return false;
        }
    }
}

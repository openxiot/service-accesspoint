package cc.openxiot.device.api.accesspoint.server.endpoint.factory;

import cn.geekcity.xiot.spec.codec.vertx.image.DeviceImageCodec;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class ProductService {

    private static final Logger logger = Logger.getLogger(ProductService.class);

    @Inject
    @RestClient
    ProductClient center;

    public Uni<DeviceImage> newInstance(String did, Summary summary) {
        logger.infov("newInstance for did={0}, type={1}", did, summary.type());

        return center.getInstance(summary.type().toString())
                .map(body -> {
                    JsonObject o = new JsonObject(body);
                    boolean success = o.getBoolean("success", false);
                    JsonObject data = o.getJsonObject("data", null);
                    if (success && data != null) {
                        return DeviceImageCodec.decode(data).did(did).summary(summary);
                    }
                    return null;
                })
                .onFailure().recoverWithItem(e -> {
                    logger.warnv("Failed to get instance for did={0}, type={1}: {2}", did, summary.type(), e.getMessage());
                    return null;
                });
    }

    public Uni<List<DeviceImage>> newInstances(List<Device> children) {
        return Multi.createFrom().iterable(children)
                .onItem()
                .transformToUniAndConcatenate(child -> newInstance(child.did(), child.summary()))
                .filter(Objects::nonNull)
                .collect().asList();
    }
}

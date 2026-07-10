package cc.openxiot.device.api.accesspoint.server;

import cc.openxiot.device.api.accesspoint.server.endpoint.factory.ProductService;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;

@Alternative
@Priority(1)
@ApplicationScoped
public class MockProductService extends ProductService {

    @Override
    public Uni<DeviceImage> newInstance(String did, Summary summary) {
        DeviceImage image = new DeviceImage(summary.type());
        image.did(did);
        image.summary(summary);
        return Uni.createFrom().item(image);
    }

    @Override
    public Uni<List<DeviceImage>> newInstances(List<Device> children) {
        List<DeviceImage> list = children.stream()
                .map(c -> {
                    DeviceImage image = new DeviceImage(c.summary().type());
                    image.did(c.did());
                    image.summary(c.summary());
                    return image;
                })
                .toList();
        return Uni.createFrom().item(list);
    }
}

package cc.openxiot.device.accesspoint;

import cc.openxiot.device.api.accesspoint.session.factory.XcpDeviceFactory;
import cn.geekcity.xiot.spec.codec.vertx.image.DeviceImageCodec;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.stream.Collectors;

@Alternative
@Priority(1)
@ApplicationScoped
public class MockXcpDeviceFactory extends XcpDeviceFactory {

    @Override
    public DeviceImage newInstance(String did, Summary summary) {
        JsonObject json = new JsonObject()
                .put("did", did)
                .put("type", summary.type())
                .put("online", summary.online())
                .put("protocol", summary.protocol());
        JsonObject root = new JsonObject().put("device", json);
        return DeviceImageCodec.decode(root);
    }

    @Override
    public List<DeviceImage> newInstances(List<Device> children) {
        return children.stream()
                .map(c -> newInstance(c.did(), c.summary()))
                .collect(Collectors.toList());
    }
}

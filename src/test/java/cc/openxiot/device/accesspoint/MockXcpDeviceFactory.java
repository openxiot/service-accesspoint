package cc.openxiot.device.accesspoint;

import cc.openxiot.device.api.accesspoint.server.endpoint.factory.XcpDeviceFactory;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
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
        DeviceImage image = new DeviceImage(summary.type());
        image.did(did);
        image.summary(summary);
        return image;
    }

    @Override
    public List<DeviceImage> newInstances(List<Device> children) {
        return children.stream()
                .map(c -> newInstance(c.did(), c.summary()))
                .collect(Collectors.toList());
    }
}

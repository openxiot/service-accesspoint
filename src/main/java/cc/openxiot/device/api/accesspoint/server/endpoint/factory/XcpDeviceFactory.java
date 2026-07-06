package cc.openxiot.device.api.accesspoint.server.endpoint.factory;

import cn.geekcity.xiot.spec.codec.vertx.image.DeviceImageCodec;
import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class XcpDeviceFactory {

    private static final Logger logger = Logger.getLogger(XcpDeviceFactory.class);

    @Inject
    @RestClient
    ProductCenter center;

    public DeviceImage newInstance(String did, Summary summary) {
        logger.infov("newInstance for did={0}, type={1}", did, summary.type());

        try {
            JsonObject o = new JsonObject(center.getInstance(summary.type().toString()));
            boolean success = o.getBoolean("success", false);
            JsonObject data = o.getJsonObject("data", null);
            if (success && data != null) {
                return DeviceImageCodec.decode(data).did(did).summary(summary);
            }
        } catch (Exception e) {
            logger.warnv("Failed to get instance for did={0}, type={1}: {2}", did, summary.type(), e.getMessage());
        }

        return null;
    }

    /**
     * 创建一个新的设备实例列表。
     *
     * @param children 设备子列表
     * @return 返回一个包含新设备实例的列表
     */
    public List<DeviceImage> newInstances(List<Device> children) {
        List<DeviceImage> images = new ArrayList<>();

        for (Device child : children) {
            DeviceImage image = newInstance(child.did(), child.summary());
            if (image != null) {
                images.add(image);
            }
        }

        return images;
    }
}

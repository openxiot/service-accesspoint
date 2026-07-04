package cc.openxiot.device.api.accesspoint.endpoint.factory;

import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class XcpDeviceFactory {

    @Inject
    @RestClient
    ProductCenter center;

    /**
     * 根据设备ID和摘要信息创建新的DeviceImage实例。
     *
     * @param did 设备ID
     * @param summary 摘要信息
     * @return 返回一个表示新创建的DeviceImage实例的Future对象
     */
    public DeviceImage newInstance(String did, Summary summary) {
        JsonObject o = center.getInstance(summary.type().toString());
        boolean success = o.getBoolean("success", false);
        if (success) {
            JsonObject data = o.getJsonObject("data");
            return DeviceImageMapper.toImage(data.toString());
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

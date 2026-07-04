package cc.openxiot.device.api.accesspoint.session.factory;

import cn.geekcity.xiot.spec.codec.vertx.image.DeviceImageCodec;
import cn.geekcity.xiot.spec.image.DeviceImage;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public class DeviceImageMapper {

    public static DeviceImage toImage(String instance) {
        try {
            JsonObject o = new JsonObject(instance);
            return DeviceImageCodec.decode(o);
        } catch (DecodeException e) {
            return null;
        }
    }
}

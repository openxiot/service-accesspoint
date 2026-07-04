package cc.openxiot.device.db.registry;

import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;

import java.util.ArrayList;

public class DeviceRegistryMapper {

    public static DeviceRegistry toEntity(DeviceImage image, String accessPoint) {
        return toEntity(image.did(), image.summary(), accessPoint);
    }

    public static DeviceRegistry toEntity(String did, Summary summary, String accessPoint) {
        DeviceRegistry entity = new DeviceRegistry();
        entity.did = did;
        entity.accessPoint = accessPoint;
        entity.type = summary.type() != null ? summary.type().toString() : null;
        entity.online = Boolean.TRUE.equals(summary.online());
        entity.protocol = summary.protocol();
        entity.parentId = summary.parentId();
        entity.rootId = summary.rootId();
        entity.memers = summary.members() != null ? new ArrayList<>(summary.members()) : null;
        entity.lastOnline = summary.lastOnline();
        entity.lastOffline = summary.lastOffline();
        return entity;
    }

    public static Device toDevice(DeviceRegistry entity) {
        Summary summary = new Summary();

        if (entity.type != null) {
            summary.type(entity.type);
        }

        summary.online(entity.online)
                .protocol(entity.protocol)
                .parentId(entity.parentId)
                .rootId(entity.rootId);

        if (entity.memers != null) {
            summary.members(entity.memers);
        }

        if (entity.lastOnline != null) {
            summary.lastOnline(entity.lastOnline);
        }

        if (entity.lastOffline != null) {
            summary.lastOffline(entity.lastOffline);
        }

        return new Device(entity.did, summary);
    }
}

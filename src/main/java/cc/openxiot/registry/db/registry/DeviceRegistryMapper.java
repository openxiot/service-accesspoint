package cc.openxiot.registry.db.registry;

import cn.geekcity.xiot.spec.device.Device;
import cn.geekcity.xiot.spec.summary.Summary;

import java.util.ArrayList;

public class DeviceRegistryMapper {

    public static DeviceRegistry toEntity(Device device) {
        DeviceRegistry entity = new DeviceRegistry();
        entity.did = device.did();
        applySummary(entity, device.summary());
        if (device instanceof cn.geekcity.xiot.spec.registry.DeviceRegistry dr) {
            entity.accessKey = dr.accessKey();
            entity.accessPoint = dr.accessPoint() != null ? dr.accessPoint().type().toString() : null;
        }
        return entity;
    }

    public static void applySummary(DeviceRegistry entity, Summary summary) {
        if (summary == null) return;
        entity.type = summary.type() != null ? summary.type().toString() : null;
        entity.online = Boolean.TRUE.equals(summary.online());
        entity.protocol = summary.protocol();
        entity.parentId = summary.parentId();
        entity.rootId = summary.rootId();
        entity.memers = summary.members() != null ? new ArrayList<>(summary.members()) : null;
        entity.lastOnline = summary.lastOnline();
        entity.lastOffline = summary.lastOffline();
    }

    public static void mergeSummary(DeviceRegistry entity, Summary summary) {
        if (summary == null) return;
        if (summary.type() != null) entity.type = summary.type().toString();
        if (summary.online() != null) entity.online = summary.online();
        if (summary.protocol() != null) entity.protocol = summary.protocol();
        if (summary.parentId() != null) entity.parentId = summary.parentId();
        if (summary.rootId() != null) entity.rootId = summary.rootId();
        if (summary.members() != null) entity.memers = new ArrayList<>(summary.members());
        if (summary.lastOnline() != null) entity.lastOnline = summary.lastOnline();
        if (summary.lastOffline() != null) entity.lastOffline = summary.lastOffline();
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
        return new cn.geekcity.xiot.spec.registry.DeviceRegistry(entity.did, summary, entity.accessKey, null, null);
    }
}

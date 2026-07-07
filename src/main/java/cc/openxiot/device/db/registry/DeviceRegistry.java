package cc.openxiot.device.db.registry;

import cn.geekcity.xiot.spec.summary.Summary;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;

@MongoEntity(collection = "registry")
@BsonDiscriminator
@RegisterForReflection
@Schema(description = "设备注册表")
public class DeviceRegistry extends PanacheMongoEntityBase {

    @BsonId
    @Schema(description = "设备ID", required = true)
    public String did;

    @Schema(description = "DeviceType", required = true)
    public String type;

    @Schema(description = "在线与否", required = true)
    public boolean online;

    @Schema(description = "设备使用的协议", required = true)
    public String protocol;

    @Schema(description = "根设备ID", required = false)
    public String rootId;

    @Schema(description = "父设备ID", required = false)
    public String parentId;

    @Schema(description = "成员设备（仅对设备组有效）", required = false)
    public List<String> members;

    @Schema(description = "最后一次上线时间", required = false)
    public Date lastOnline;

    @Schema(description = "最后一次离线时间", required = false)
    public Date lastOffline;

    @Schema(description = "访问秘钥", required = true)
    public String accessKey;

    @Schema(description = "接入点", required = true)
    public String accessPoint;

    @Schema(description = "创建时间", required = false)
    public Date create;

    public static DeviceRegistry of(String did, Summary summary, DeviceRegistry root) {
        DeviceRegistry x = new DeviceRegistry();

        x.did = did;
        x.type = summary.type().toString();
        x.online = summary.online();
        x.rootId = root.did;
        x.parentId = summary.parentId();
        x.members = summary.members();
        x.lastOnline = summary.lastOnline();
        x.lastOffline = summary.lastOffline();
        x.accessKey = root.accessKey;
        x.accessPoint = root.accessPoint;
        x.create = new Date();

        return x;
    }

    public boolean change(Summary summary, DeviceRegistry root) {
        boolean changed = false;

        if (this.type != null) {
            String t = summary.type().toString();
            if (! this.type.equals(t)) {
                this.type = t;
                changed = true;
            }
        }

        if (online != summary.online()) {
            online = summary.online();
            changed = true;
        }

        if (rootId != null) {
            if (! rootId.equals(root.did)) {
                rootId = root.did;
                changed = true;
            }
        } else {
            rootId = root.did;
            changed = true;
        }

        if (parentId != null) {
            if (! parentId.equals(summary.parentId())) {
                parentId = summary.parentId();
                changed = true;
            }
        } else {
            if (summary.parentId() != null) {
                parentId = summary.parentId();
                changed = true;
            }
        }

        if (! members.equals(summary.members())) {
            members = summary.members();
            changed = true;
        }

        if (! accessKey.equals(root.accessKey)) {
            accessKey = root.accessKey;
            changed = true;
        }

        if (! accessPoint.equals(root.accessPoint)) {
            accessPoint = root.accessPoint;
            changed = true;
        }

        return changed;
    }
}

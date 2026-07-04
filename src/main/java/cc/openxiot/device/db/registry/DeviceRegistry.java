package cc.openxiot.device.db.registry;

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

    @Schema(description = "父设备ID", required = false)
    public String parentId;

    @Schema(description = "根设备ID", required = false)
    public String rootId;

    @Schema(description = "成员设备（仅对设备组有效）", required = false)
    public List<String> memers;

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
}

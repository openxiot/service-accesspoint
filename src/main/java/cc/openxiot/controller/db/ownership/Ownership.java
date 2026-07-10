package cc.openxiot.controller.db.ownership;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;

@BsonDiscriminator
@RegisterForReflection
@Schema(description = "从属关系")
public class Ownership {

    @BsonId
    @Schema(description = "复合主键", required = true)
    public OwnershipId id;

    @Schema(description = "设备ID", required = true)
    public String did;

    @Schema(description = "设备秘钥", required = true)
    public String key;

    @Schema(description = "应用ID", required = true)
    public String appId;

    @Schema(description = "拥有者ID", required = true)
    public String ownerId;

    @Schema(description = "创建时间", required = false)
    public Date create;

    @Schema(description = "更新时间", required = false)
    public Date update;

    public static Ownership of(String did, String appId, String ownerId, String key) {
        Ownership o = new Ownership();

        o.id = new OwnershipId(did, appId, ownerId);

        o.did = did;
        o.appId = appId;
        o.ownerId = ownerId;
        o.key = key;
        o.create = new Date();
        o.update = new Date();

        return o;
    }
}

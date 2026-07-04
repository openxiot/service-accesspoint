package cc.openxiot.controller.db.ownership;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;

@MongoEntity(collection = "ownership")
@BsonDiscriminator
@RegisterForReflection
@Schema(description = "从属关系")
public class Ownership extends PanacheMongoEntityBase {

    @BsonId
    @Schema(description = "复合主键", required = true)
    public OwnershipKey id;

    @Schema(description = "设备ID", required = true)
    public String did;

    @Schema(description = "设备Token", required = true)
    public String token;

    @Schema(description = "应用ID", required = true)
    public String appId;

    @Schema(description = "拥有者ID", required = true)
    public String ownerId;

    @Schema(description = "创建时间", required = false)
    public Date create;

    @Schema(description = "更新时间", required = false)
    public Date update;
}

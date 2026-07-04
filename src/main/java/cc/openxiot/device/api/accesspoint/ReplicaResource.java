package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.response.OxResponse;
import cc.openxiot.device.api.accesspoint.replica.ReplicaService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@Path("/v1/replica")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Replica", description = "Replica API")
@RequestScoped
public class ReplicaResource {

    @Inject
    Logger logger;

    @Inject
    ReplicaService replica;

    @GET
    @Path("/id")
    public Response getId() {
        // 使用指定的环境变量名称来读取实例ID
        //  Azure Container App: CONTAINER_APP_REPLICA_NAME
        //  Alibaba Cloud Serverless: EDAS_APP_ID
        //  Amazon ECS: ECS_CONTAINER_METADATA_URI_V4

        String replicaName = System.getenv("CONTAINER_APP_REPLICA_NAME");
        logger.warnv("Replica name: {0}", replicaName);

        // 如果变量不存在，可以fallback到HOSTNAME或unknown
        String instanceId = replicaName != null ? replicaName : System.getenv("HOSTNAME");
        logger.warnv("Instance ID: {0}", instanceId);

        return OxResponse.ok(instanceId != null ? instanceId : "unknown");
    }

    @GET
    @Path("/ip")
    public Response getIp() {
        String ip = replica.getIp();
        logger.infov("Container IP: {0}", ip);
        return OxResponse.ok(ip);
    }
}

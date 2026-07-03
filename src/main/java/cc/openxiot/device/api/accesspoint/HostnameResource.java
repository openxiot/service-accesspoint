package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.response.OxResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@Path("/v1/hostname")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Hostname", description = "Hostname API")
@RequestScoped
public class HostnameResource {

    @Inject
    Logger logger;

    @GET
    public Response getHostname() {
        // 优先使用 ACA 提供的副本名称来标识实例
        String replicaName = System.getenv("CONTAINER_APP_REPLICA_NAME");
        // 如果变量不存在，可以fallback到HOSTNAME或unknown
        String instanceId = replicaName != null ? replicaName : System.getenv("HOSTNAME");
        logger.infov("Instance ID: {0}", instanceId);
        return OxResponse.ok(instanceId != null ? instanceId : "unknown");
    }
}

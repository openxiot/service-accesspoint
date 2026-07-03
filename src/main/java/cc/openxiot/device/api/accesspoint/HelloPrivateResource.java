package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.filter.PrivateNetwork;
import cc.openxiot.common.response.OxResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@PrivateNetwork
@Path("/v1/hello/private")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Hello Private", description = "Private Hello API (internal network only)")
@RequestScoped
public class HelloPrivateResource {

    @Inject
    Logger logger;

    @Inject
    DeviceSessionManager sessionManager;

    @GET
    public Response hello() {
        logger.info("Private hello");
        return OxResponse.ok("Hello from private");
    }

    @GET
    @Path("/session/{did}")
    public Response getSession(@PathParam("did") String did) {
        DeviceSession ds = sessionManager.get(did);
        if (ds == null) {
            return OxResponse.ok(java.util.Map.of("did", did, "cn", "not found"));
        }
        return OxResponse.ok(java.util.Map.of("did", ds.did, "cn", ds.cn));
    }
}

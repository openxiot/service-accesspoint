package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.filter.PrivateNetwork;
import cc.openxiot.common.response.OxResponse;
import cc.openxiot.device.api.accesspoint.session.DeviceSession;
import cc.openxiot.device.api.accesspoint.session.DeviceSessionManager;
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

import java.util.Map;

@PrivateNetwork
@Path("/v1/private")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Private API", description = "Private API (internal network only)")
@RequestScoped
public class XcpDeviceServerResource {

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
        DeviceSession s = sessionManager.get(did);
        if (s == null) {
            return OxResponse.ok(Map.of("did", did, "status", "offline"));
        }
        return OxResponse.ok(Map.of("did", s.did, "status", "online"));
    }
}

package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.filter.PrivateNetwork;
import cc.openxiot.common.response.OxResponse;
import cc.openxiot.device.api.accesspoint.session.XcpDeviceEndpoint;
import cc.openxiot.device.api.accesspoint.session.XcpDeviceEndpointManager;
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
    XcpDeviceEndpointManager manager;

    @GET
    public Response hello() {
        logger.info("Private hello");
        return OxResponse.ok("Hello from private");
    }

    @GET
    @Path("/session/{id}")
    public Response getSession(@PathParam("id") String id) {
        XcpDeviceEndpoint s = manager.getEndpoint(id);
        if (s == null) {
            return OxResponse.ok(Map.of("id", id, "status", "offline"));
        }
        return OxResponse.ok(Map.of("id", s.id(), "status", "online"));
    }
}

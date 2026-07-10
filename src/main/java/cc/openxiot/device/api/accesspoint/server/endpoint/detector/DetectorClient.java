package cc.openxiot.device.api.accesspoint.server.endpoint.detector;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/private/probe")
@Produces(MediaType.TEXT_PLAIN)
public interface DetectorClient {

    @GET
    Uni<String> probe(@QueryParam("did") String did);
}

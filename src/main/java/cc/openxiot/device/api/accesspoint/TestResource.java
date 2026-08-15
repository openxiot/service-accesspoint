package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.response.OxResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "TEST API", description = "TEST API")
@RequestScoped
public class TestResource {

    @Inject
    Logger logger;

    @GET
    @Path("/")
    public Response test() {
        logger.info("test");
        return OxResponse.ok();
    }
}

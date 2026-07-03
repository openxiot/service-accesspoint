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

@Path("/v1/hello")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Hello", description = "Public Hello API")
@RequestScoped
public class HelloResource {

    @Inject
    Logger logger;

    @GET
    public Response hello() {
        logger.info("Public hello");
        return OxResponse.ok("Hello from public");
    }
}

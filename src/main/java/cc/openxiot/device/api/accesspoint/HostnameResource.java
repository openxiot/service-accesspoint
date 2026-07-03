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
        String hostname = System.getenv("HOSTNAME");
        logger.infov("HOSTNAME: {0}", hostname);
        return OxResponse.ok(hostname != null ? hostname : "unknown");
    }
}

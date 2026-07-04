package cc.openxiot.device.api.accesspoint.endpoint.factory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/v1/product")
@RegisterRestClient(configKey = "product-center-api")
public interface ProductCenter {

    @GET
    @Path("/instance/one/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    String getInstance(@PathParam("type") String type);
}

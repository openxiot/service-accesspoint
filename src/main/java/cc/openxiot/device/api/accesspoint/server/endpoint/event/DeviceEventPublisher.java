package cc.openxiot.device.api.accesspoint.server.endpoint.event;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/v1/event")
@RegisterRestClient(configKey = "device-event-api")
public interface DeviceEventPublisher {

    @POST
    @Path("/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    String publish(@PathParam("type") String type, String body);
}

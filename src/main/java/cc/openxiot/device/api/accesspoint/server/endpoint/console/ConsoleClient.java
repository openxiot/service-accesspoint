package cc.openxiot.device.api.accesspoint.server.endpoint.console;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/v1/private/device")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "console-api")
public interface ConsoleClient {

    @GET
    @Path("/apply/one")
    String applyOne(
            @QueryParam("orgId") String orgId,
            @QueryParam("key") String key,
            @QueryParam("issueCert") @DefaultValue("false") boolean  issueCert
    );

    @GET
    String applyMany(
            @QueryParam("orgId") String orgId,
            @QueryParam("key") List<String> keys,
            @QueryParam("issueCert") @DefaultValue("false") boolean issueCert
    );

    @GET
    @Path("/probe/one")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<String> probeOne(@QueryParam("did") String did);

    @GET
    @Path("/probe/many")
    Uni<String> probeMany(@QueryParam("did") List<String> dids);
}

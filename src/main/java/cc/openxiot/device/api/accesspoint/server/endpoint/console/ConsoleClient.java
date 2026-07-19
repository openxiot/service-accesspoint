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
    Uni<String> applyOne(
            @QueryParam("orgId") String orgId,
            @QueryParam("signature") String signature,
            @QueryParam("deviceFingerprint") String fingerprint,
            @QueryParam("issueCert") @DefaultValue("false") boolean  issueCert
    );

    @GET
    Uni<String> applyMany(
            @QueryParam("orgId") String orgId,
            @QueryParam("signature") String signature,
            @QueryParam("deviceFingerprints") List<String> fingerprints,
            @QueryParam("issueCert") @DefaultValue("false") boolean issueCert
    );

    @GET
    @Path("/active/one")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<String> activeOne(@QueryParam("did") String did);

    @GET
    @Path("/active/many")
    Uni<String> activeMany(@QueryParam("did") List<String> dids);
}

package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.response.OxResponse;
import cc.openxiot.device.api.accesspoint.server.endpoint.console.ConsoleService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;

@Path("/v1/apply")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Apply API", description = "Apply API")
@RequestScoped
public class ApplyResource {

    @Inject
    Logger logger;

    @Inject
    ConsoleService service;

    @GET
    @Path("/hello")
    public Response hello() {
        return OxResponse.ok("Hello from ApplyResource");
    }

    @GET
    @Path("/one")
    public Uni<Response> applyDeviceCert(
            @QueryParam("orgId") String orgId,
            @QueryParam("signature") String signature,
            @QueryParam("fingerprint") String fingerprint,
            @QueryParam("issueCert") @DefaultValue("false") boolean  issueCert
    ) {
        return service.applyOne(orgId, signature, fingerprint, issueCert)
                .map(OxResponse::ok)
                .onFailure().recoverWithItem(e -> OxResponse.error(e.getMessage()));
    }

    @GET
    @Path("/many")
    @Operation(summary = "批量申请多个设备ID", description = "为一个开发组批量申请多个设备ID，通过重复deviceFingerprint参数传入多个指纹。issueCert=true时自动签发证书")
    public Uni<Response> applyMany(
            @QueryParam("orgId") String orgId,
            @QueryParam("signature") String signature,
            @QueryParam("fingerprints") List<String> fingerprints,
            @QueryParam("issueCert") @DefaultValue("false") boolean issueCert
    ) {
        logger.infov("apply deviceIds for org: {0}, deviceFingerprints: {1}, issueCert: {2}", orgId, fingerprints, issueCert);
        return service.applyMany(orgId, signature, fingerprints, issueCert)
                .map(OxResponse::ok)
                .onFailure().recoverWithItem(e -> OxResponse.error(e.getMessage()));
    }
}

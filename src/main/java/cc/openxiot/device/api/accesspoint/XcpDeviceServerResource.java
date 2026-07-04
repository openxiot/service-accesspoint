package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.filter.PrivateNetwork;
import cc.openxiot.common.response.OxResponse;
import cc.openxiot.device.api.accesspoint.session.XcpDeviceEndpoint;
import cc.openxiot.device.api.accesspoint.session.XcpDeviceEndpointManager;
import cn.geekcity.xiot.spec.codec.vertx.operation.PropertyOperationCodec;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

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

    @GET
    @Path("/properties")
    public CompletionStage<Response> getProperties(
            @QueryParam("pid") List<String> pid,
            @Context HttpHeaders headers
    ) {
        // traceId: 优先取链路追踪的 X-Request-Id，没有则生成 UUID
        String traceId = headers.getHeaderString("X-Request-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        List<PropertyOperation> properties = pid.stream().map(PropertyOperation::new).toList();

        return manager.getProperties(traceId, properties)
                .map(list -> OxResponse.ok(PropertyOperationCodec.Get.RESULT.encode(list)))
                .otherwise(e -> OxResponse.error(e.getMessage()))
                .toCompletionStage();
    }
}

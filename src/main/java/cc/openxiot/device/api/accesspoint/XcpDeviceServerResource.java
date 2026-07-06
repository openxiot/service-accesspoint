package cc.openxiot.device.api.accesspoint;

import cc.openxiot.common.filter.PrivateNetwork;
import cc.openxiot.common.response.OxResponse;
import cc.openxiot.device.api.accesspoint.server.endpoint.XcpDeviceEndpoint;
import cc.openxiot.device.api.accesspoint.server.endpoint.XcpDeviceEndpointManager;
import cn.geekcity.xiot.spec.codec.vertx.device.DeviceCodec;
import cn.geekcity.xiot.spec.codec.vertx.operation.ActionOperationCodec;
import cn.geekcity.xiot.spec.codec.vertx.operation.PropertyOperationCodec;
import cn.geekcity.xiot.spec.operation.ActionOperation;
import cn.geekcity.xiot.spec.operation.PropertyOperation;
import io.vertx.core.json.JsonArray;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

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
    @Path("/devices")
    public Response getDevices() {
        return OxResponse.ok(DeviceCodec.encode(manager.getDevices()));
    }

    @GET
    @Path("/properties")
    public CompletionStage<Response> getProperties(@Context HttpHeaders headers, @QueryParam("pid") List<String> pid) {
        String traceId = resolveTraceId(headers);
        if (pid == null || pid.isEmpty()) {
            return CompletableFuture.completedFuture(OxResponse.error("pid is required"));
        }
        List<PropertyOperation> properties = pid.stream().map(PropertyOperation::new).toList();
        return manager.getProperties(traceId, properties)
                .map(list -> OxResponse.ok(PropertyOperationCodec.Get.RESULT.encode(list)))
                .otherwise(e -> OxResponse.error(e.getMessage()))
                .toCompletionStage();
    }

    @POST
    @Path("/properties")
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> setProperties(@Context HttpHeaders headers, String body) {
        String traceId = resolveTraceId(headers);
        JsonArray array = new JsonArray(body);
        List<PropertyOperation> properties = array.stream()
                .map(PropertyOperationCodec.Set.QUERY::decode)
                .collect(Collectors.toList());
        return manager.setProperties(traceId, properties)
                .map(list -> OxResponse.ok(PropertyOperationCodec.Set.RESULT.encode(list)))
                .otherwise(e -> OxResponse.error(e.getMessage()))
                .toCompletionStage();
    }

    @POST
    @Path("/actions")
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> invokeActions(@Context HttpHeaders headers, String body) {
        String traceId = resolveTraceId(headers);
        JsonArray array = new JsonArray(body);
        List<ActionOperation> actions = array.stream()
                .map(ActionOperationCodec.QUERY::decode)
                .collect(Collectors.toList());
        return manager.invokeActions(traceId, actions)
                .map(list -> OxResponse.ok(ActionOperationCodec.RESULT.encode(list)))
                .otherwise(e -> OxResponse.error(e.getMessage()))
                .toCompletionStage();
    }

    private static String resolveTraceId(HttpHeaders headers) {
        String traceId = headers.getHeaderString("X-Request-Id");
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }
}

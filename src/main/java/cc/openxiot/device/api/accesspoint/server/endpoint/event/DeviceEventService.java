package cc.openxiot.device.api.accesspoint.server.endpoint.event;

import cn.geekcity.xiot.spec.codec.vertx.notice.DeviceNoticeCodec;
import cn.geekcity.xiot.spec.notice.device.DeviceNotice;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class DeviceEventService {

    @Inject
    @RestClient
    DeviceEventPublisher publisher;

    DeviceNoticeCodec codec = new DeviceNoticeCodec();

    public void publish(DeviceNotice notice) {
        JsonObject o = codec.encode(notice);
        publisher.publish(notice.subType(), o.encode());
    }
}

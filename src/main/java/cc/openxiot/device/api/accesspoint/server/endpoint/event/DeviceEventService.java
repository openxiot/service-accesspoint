package cc.openxiot.device.api.accesspoint.server.endpoint.event;

import cn.geekcity.xiot.spec.codec.vertx.notice.DeviceNoticeCodec;
import cn.geekcity.xiot.spec.notice.device.DeviceNotice;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DeviceEventService {

    @Inject
    Logger logger;

    @Inject
    @RestClient
    DeviceEventPublisher publisher;

    DeviceNoticeCodec codec = new DeviceNoticeCodec();

    public void publish(DeviceNotice notice) {
        String body = codec.encode(notice).toString();
        logger.infov("publish: {0}", body);
        publisher.publish(notice.subType(), body)
                .subscribe().with(
                        result -> logger.infov("publish success: {0}", result),
                        e -> logger.error("publish error: ", e)
                );
    }
}

package cc.openxiot.device.api.accesspoint.session;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class XcpDeviceEndpointHandler {

    @Inject
    Logger logger;

    public void onActive(XcpDeviceEndpoint endpoint) {
        logger.infov("onActive: {0}", endpoint.root().did());
    }

    public void onInactive(XcpDeviceEndpoint endpoint) {
        logger.infov("onInactive: {0}", endpoint.root().did());
    }
}

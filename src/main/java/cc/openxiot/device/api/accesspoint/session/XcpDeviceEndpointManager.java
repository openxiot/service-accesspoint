package cc.openxiot.device.api.accesspoint.session;

import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.notice.Notice;
import cn.geekcity.xiot.spec.shadow.Shadow;
import cn.geekcity.xiot.xcp.stanza.iq.IQ;
import cn.geekcity.xiot.xcp.stanza.iq.device.control.*;
import cn.geekcity.xiot.xcp.stanza.message.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class XcpDeviceEndpointManager {

    @Inject
    Logger logger;

    @Inject
    XcpDeviceEndpointHandler handler;

    private final ConcurrentHashMap<String, XcpDeviceEndpoint> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> devices = new ConcurrentHashMap<>();

    public void add(XcpDeviceEndpoint endpoint) {
        endpoints.put(endpoint.id(), endpoint);

        // save: <did, endpointId> => devices
        save(endpoint.id(), endpoint.root());

        // 修正根设备的在线时间。
        endpoint.root().summary().lastOnline(new Date());

        handler.onActive(endpoint);
    }

    private void save(String endpointId, DeviceImage device) {
        logger.info(String.format("save, did %s on endpoint(%s)", device.did(), endpointId));
        devices.put(device.did(), endpointId);
        for (DeviceImage child : device.children().values()) {
            save(endpointId, child);
        }
    }

    public void remove(String id) {
        XcpDeviceEndpoint endpoint = endpoints.remove(id);
        if (endpoint != null) {
            devices.remove(endpoint.root().did());
            handler.onInactive(endpoint);
        }
    }

    public XcpDeviceEndpoint getEndpoint(String did) {
        String endpointId = devices.get(did);
        return endpointId != null ? endpoints.get(endpointId) : null;
    }

    public Collection<XcpDeviceEndpoint> getEndpoints() {
        return endpoints.values();
    }

    public List<Shadow> getShadow(String did) {
        return null;
    }

    void send(String did, Message<? extends Notice> message) {

    }

    public IQ getProperties(GetProperties.Query query) {
        return null;
    }

    public IQ setProperties(SetProperties.Query query) {
        return null;
    }

    public IQ invokeActions(InvokeActions.Query query) {
        return null;
    }

    public IQ getProperty(GetProperty.Query query) {
        return null;
    }

    public IQ setProperty(SetProperty.Query query) {
        return null;
    }

    public IQ invokeAction(InvokeAction.Query query) {
        return null;
    }
}

package cc.openxiot.device.api.accesspoint;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DeviceSessionManager {

    private final ConcurrentHashMap<String, DeviceSession> sessions = new ConcurrentHashMap<>();

    public void register(String did, DeviceSession session) {
        sessions.put(did, session);
    }

    public void unregister(String did) {
        sessions.remove(did);
    }

    public DeviceSession get(String did) {
        return sessions.get(did);
    }
}

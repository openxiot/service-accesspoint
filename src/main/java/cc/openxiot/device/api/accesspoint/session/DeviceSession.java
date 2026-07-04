package cc.openxiot.device.api.accesspoint.session;

import jakarta.websocket.Session;

public class DeviceSession {
    public final String did;
    public final Session session;

    public DeviceSession(String did, Session session) {
        this.did = did;
        this.session = session;

        // session.getAsyncRemote().sendText("hello");
    }
}

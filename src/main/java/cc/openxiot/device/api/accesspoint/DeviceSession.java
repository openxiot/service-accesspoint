package cc.openxiot.device.api.accesspoint;

import jakarta.websocket.Session;

public class DeviceSession {
    public final String did;
    public final String cn;
    public final Session session;

    public DeviceSession(String did, String cn, Session session) {
        this.did = did;
        this.cn = cn;
        this.session = session;

        // session.getAsyncRemote().sendText("hello");
    }
}

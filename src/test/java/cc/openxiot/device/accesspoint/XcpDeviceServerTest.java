package cc.openxiot.device.accesspoint;

import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import cn.geekcity.xiot.xcp.stanza.iq.IQResult;
import cn.geekcity.xiot.xcp.stanza.iq.basic.Ping;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.websocket.*;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class XcpDeviceServerTest {

    private final StanzaCodec stanzaCodec = StanzaCodec.getInstance();

    @Test
    void testPingPong() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();

        TestClient client = new TestClient();
        client.latch = latch;
        client.response = response;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        Session session = container.connectToServer(client,
                URI.create("ws://localhost:8081/test-device-001"));

        String pingJson = stanzaCodec.encode(new Ping.Query("ping-1")).encode();
        session.getAsyncRemote().sendText(pingJson);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for pong");
        assertNotNull(response.get());

        IQResult result = (IQResult) stanzaCodec.decode(new io.vertx.core.json.JsonObject(response.get()));
        assertEquals("ping-1", result.id());

        session.close();
    }

    @ClientEndpoint
    static class TestClient {
        CountDownLatch latch;
        AtomicReference<String> response;

        @OnMessage
        void onMessage(String msg) {
            response.set(msg);
            latch.countDown();
        }
    }
}

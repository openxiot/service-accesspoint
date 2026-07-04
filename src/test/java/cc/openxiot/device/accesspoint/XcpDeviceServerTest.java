package cc.openxiot.device.accesspoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.websocket.*;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class XcpDeviceServerTest {

    @Test
    void testConnectionEstablished() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Session> opened = new AtomicReference<>();

        TestClient client = new TestClient();
        client.onOpen = s -> {
            opened.set(s);
            latch.countDown();
        };

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        Session session = container.connectToServer(client,
                URI.create("ws://localhost:8081/v1/test-device-001/gateway"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for onOpen");
        assertTrue(opened.get().isOpen());

        session.close();
    }

    @ClientEndpoint
    static class TestClient {
        Consumer<Session> onOpen;

        @OnOpen
        void onOpen(Session session) {
            if (onOpen != null) {
                onOpen.accept(session);
            }
        }

        @OnMessage
        void onMessage(String msg) {
        }
    }
}

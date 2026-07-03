package cc.openxiot.device.accesspoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.websocket.*;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class XcpServerTest {

    @Test
    void testEcho() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> echo = new AtomicReference<>();
        AtomicReference<String> welcome = new AtomicReference<>();

        EchoClient client = new EchoClient();
        client.latch = latch;
        client.welcome = welcome;
        client.echo = echo;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        Session session = container.connectToServer(client,
                URI.create("ws://localhost:8081/ws/echo"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for messages");
        assertNotNull(welcome.get());
        assertTrue(welcome.get().startsWith("Connected to"));
        assertEquals("echo: hello", echo.get());

        session.close();
    }

    @ClientEndpoint
    static class EchoClient {
        CountDownLatch latch;
        AtomicReference<String> welcome;
        AtomicReference<String> echo;

        @OnOpen
        void onOpen(Session session) {
            session.getAsyncRemote().sendText("hello");
        }

        @OnMessage
        void onMessage(String msg) {
            if (welcome.get() == null) {
                welcome.set(msg);
            } else {
                echo.set(msg);
            }
            latch.countDown();
        }
    }
}

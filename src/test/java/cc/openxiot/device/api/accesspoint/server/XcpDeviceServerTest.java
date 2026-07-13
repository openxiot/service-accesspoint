package cc.openxiot.device.api.accesspoint.server;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import jakarta.websocket.*;
import java.net.URI;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class XcpDeviceServerTest {

    private Session session;

    @AfterEach
    void tearDown() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
        // Drain pending server-side async operations (REST call to ProductCenter,
        // Mutiny Uni chain from @OnOpen) before Quarkus shuts down Vert.x.
        // Without this wait, unfinished callbacks hit RejectedExecutionException
        // on the terminated event executor and produce ERROR log noise.
        Thread.sleep(1000);
    }

    @Test
    void testConnectionEstablished() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        TestClient client = new TestClient();
        client.onOpen = s -> latch.countDown();

        session = connect(client);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for onOpen");
        assertTrue(session.isOpen());
    }

    @Test
    @Disabled("requires Docker (MongoDB Dev Services) for endpoint registration")
    void testPingPong() throws Exception {
        TestDeviceClient client = new TestDeviceClient();
        session = connect(client);

        assertTrue(client.connected.await(10, TimeUnit.SECONDS), "timed out waiting for onOpen");

        // Wait for the server to finish processing onOpen and register the endpoint
        // (MongoDB query + Mutiny chain for isOnline + onActive + initialize)
        Thread.sleep(2000);

        // Send Ping query as raw JSON stanza
        String pingQuery = "{\"iq\":{\"id\":\"test-ping-1\",\"type\":\"query\",\"method\":\"urn:xiot:ping\"}}";
        session.getBasicRemote().sendText(pingQuery);

        // Wait for Pong result (or any other message, filter for the expected one)
        String response = null;
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String msg = client.received.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null) {
                if (msg.contains("\"method\":\"urn:xiot:ping\"") && msg.contains("\"type\":\"result\"")) {
                    response = msg;
                    break;
                }
            }
        }

        assertNotNull(response, "Should receive Ping result within timeout");

        // Verify Ping result fields
        assertTrue(response.contains("\"id\":\"test-ping-1\""),
                "Ping result should contain the original query id");
    }

    @Test
    @Disabled("requires Docker (MongoDB Dev Services) for endpoint registration")
    void testInvalidStanza_returnsError() throws Exception {
        TestDeviceClient client = new TestDeviceClient();
        session = connect(client);

        assertTrue(client.connected.await(10, TimeUnit.SECONDS));
        Thread.sleep(2000);

        // Send invalid JSON
        session.getBasicRemote().sendText("invalid json");

        String response = client.received.poll(5, TimeUnit.SECONDS);
        assertNotNull(response, "Should receive error response for invalid JSON");
        assertTrue(response.contains("\"type\":\"error\""),
                "Invalid JSON should return an IQ error: " + response);
    }

    private Session connect(Object client) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        return container.connectToServer(client,
                URI.create("ws://localhost:8081/v1/test-device-001/gateway"));
    }

    /**
     * Minimal WebSocket client for basic connection testing.
     */
    @ClientEndpoint
    static class TestClient {
        volatile Consumer<Session> onOpen;

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

    /**
     * WebSocket device client that can receive and send XCP stanzas.
     */
    @ClientEndpoint
    static class TestDeviceClient {
        final CountDownLatch connected = new CountDownLatch(1);
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();

        @OnOpen
        void onOpen(Session session) {
            connected.countDown();
        }

        @OnMessage
        void onMessage(String text) {
            received.offer(text);
        }
    }

    @FunctionalInterface
    interface Consumer<T> {
        void accept(T t);
    }
}

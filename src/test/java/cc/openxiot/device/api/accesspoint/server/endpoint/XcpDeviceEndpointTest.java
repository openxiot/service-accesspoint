package cc.openxiot.device.api.accesspoint.server.endpoint;

import cn.geekcity.xiot.spec.image.DeviceImage;
import cn.geekcity.xiot.spec.summary.Summary;
import cn.geekcity.xiot.xcp.stanza.codec.vertx.impl.StanzaCodec;
import io.vertx.core.Vertx;
import jakarta.websocket.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XcpDeviceEndpoint stanza routing and handling logic.
 * Creates XcpDeviceEndpoint with a mock Session, bypassing WebSocket/MongoDB/Quarkus.
 */
class XcpDeviceEndpointTest {

    private static final String DID = "test-device-001";
    private static final String TYPE = "urn:test:device:switch:00000008";

    private Vertx vertx;
    private XcpDeviceEndpoint endpoint;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();

        Summary summary = new Summary(TYPE, true, "wss", null, DID);
        DeviceImage image = new DeviceImage(summary.type());
        image.did(DID);
        image.summary(summary);

        MockSession.sentMessages.clear();
        endpoint = new XcpDeviceEndpoint(vertx, "192.168.1.1", new MockSession(), image, StanzaCodec.getInstance());
        endpoint.handler(new XcpDeviceEndpointHandlerStub());
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void onReceive_pingQuery_respondsWithResult() {
        endpoint.onReceive("{\"iq\":{\"id\":\"test-1\",\"type\":\"query\",\"method\":\"urn:xiot:ping\"}}");

        assertEquals(1, MockSession.sentMessages.size(), "Should send exactly one response");

        String response = MockSession.sentMessages.getFirst();
        assertTrue(response.contains("\"type\":\"result\""), "Response should be a result");
        assertTrue(response.contains("\"method\":\"urn:xiot:ping\""), "Response should be for ping");
        assertTrue(response.contains("\"id\":\"test-1\""), "Response should echo query id");
    }

    @Test
    void onReceive_invalidJson_returnsError() {
        endpoint.onReceive("not valid json");

        assertEquals(1, MockSession.sentMessages.size(), "Should send error response");

        String response = MockSession.sentMessages.getFirst();
        assertTrue(response.contains("\"type\":\"error\""), "Invalid JSON should produce IQ error");
        assertTrue(response.contains("invalid json"), "Error description should mention invalid json");
    }

    @Test
    void onReceive_unknownMethod_returnsNotSupported() {
        endpoint.onReceive("{\"iq\":{\"id\":\"test-2\",\"type\":\"query\",\"method\":\"urn:xiot:unknown\"}}");

        assertEquals(1, MockSession.sentMessages.size(), "Should send error for unknown method");

        String response = MockSession.sentMessages.getFirst();
        assertTrue(response.contains("\"type\":\"error\""), "Unknown query should produce IQ error");
        assertTrue(response.contains("\"type\":\"error\""), "Unknown query should produce IQ error");
        assertTrue(response.contains("Query Handler not found"), "Error description should mention unknown method");
    }

    @Test
    void onReceive_resultWithoutHandler_isIgnored() {
        endpoint.onReceive("{\"iq\":{\"id\":\"no-such-1\",\"type\":\"result\",\"method\":\"urn:xiot:ping\"}}");

        assertTrue(MockSession.sentMessages.isEmpty(), "No message should be sent for unmatched result");
    }

    @Test
    void onReceive_errorWithoutHandler_isIgnored() {
        endpoint.onReceive("{\"iq\":{\"id\":\"no-such-2\",\"type\":\"error\",\"content\":{\"status\":500,\"description\":\"internal error\"}}}");

        assertTrue(MockSession.sentMessages.isEmpty(), "No message should be sent for unmatched error");
    }

    // -----------------------------------------------------------------------
    // Mock Session — records all sent text messages in sentMessages
    // -----------------------------------------------------------------------

    static class MockSession implements Session {
        static final List<String> sentMessages = new ArrayList<>();
        private long maxIdleTimeout;
        private boolean open = true;
        private final String id = UUID.randomUUID().toString().split("-")[4];

        @Override
        public RemoteEndpoint.Async getAsyncRemote() {
            return new RemoteEndpoint.Async() {
                @Override public void sendText(String text, SendHandler handler) {
                    sentMessages.add(text);
                    if (handler != null) handler.onResult(new SendResult());
                }
                @Override public Future<Void> sendText(String text) {
                    sentMessages.add(text);
                    return CompletableFuture.completedFuture(null);
                }
                @Override public void sendBinary(ByteBuffer data, SendHandler handler) {}
                @Override public Future<Void> sendBinary(ByteBuffer data) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public void sendObject(Object data, SendHandler handler) {}
                @Override public Future<Void> sendObject(Object data) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public long getSendTimeout() { return 0; }
                @Override public void setSendTimeout(long t) {}
                @Override public void setBatchingAllowed(boolean allowed) {}
                @Override public boolean getBatchingAllowed() { return false; }
                @Override public void flushBatch() {}
                @Override public void sendPing(ByteBuffer data) {}
                @Override public void sendPong(ByteBuffer data) {}
            };
        }

        @Override public String getId() { return id; }
        @Override public void setMaxIdleTimeout(long ms) { this.maxIdleTimeout = ms; }
        @Override public long getMaxIdleTimeout() { return maxIdleTimeout; }
        @Override public boolean isOpen() { return open; }
        @Override public void close(CloseReason reason) { open = false; }
        @Override public void close() { open = false; }

        @Override public WebSocketContainer getContainer() { return null; }
        @Override public void addMessageHandler(MessageHandler handler) {}
        @Override public <T> void addMessageHandler(Class<T> c, MessageHandler.Whole<T> h) {}
        @Override public <T> void addMessageHandler(Class<T> c, MessageHandler.Partial<T> h) {}
        @Override public Set<MessageHandler> getMessageHandlers() { return Set.of(); }
        @Override public void removeMessageHandler(MessageHandler handler) {}
        @Override public String getProtocolVersion() { return null; }
        @Override public String getNegotiatedSubprotocol() { return null; }
        @Override public List<Extension> getNegotiatedExtensions() { return List.of(); }
        @Override public boolean isSecure() { return false; }
        @Override public void setMaxBinaryMessageBufferSize(int s) {}
        @Override public int getMaxBinaryMessageBufferSize() { return 0; }
        @Override public void setMaxTextMessageBufferSize(int s) {}
        @Override public int getMaxTextMessageBufferSize() { return 0; }
        @Override public RemoteEndpoint.Basic getBasicRemote() { return null; }
        @Override public URI getRequestURI() { return null; }
        @Override public Map<String, List<String>> getRequestParameterMap() { return Map.of(); }
        @Override public String getQueryString() { return null; }
        @Override public Map<String, String> getPathParameters() { return Map.of(); }
        @Override public Map<String, Object> getUserProperties() { return Map.of(); }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public Set<Session> getOpenSessions() { return Set.of(); }
    }

    // -----------------------------------------------------------------------
    // Stub handler — overrides all business logic callbacks as no-ops
    // to avoid null @Inject fields in the parent class.
    // -----------------------------------------------------------------------

    static class XcpDeviceEndpointHandlerStub extends XcpDeviceEndpointHandler {
        // All parent methods use @Inject services (registry, event, ownership).
        // Since this is a pure unit test without CDI, those fields are null.
        // Override each method that could be called to prevent NPE.
        // For stanza routing tests (ping, unknown method, etc.), none of the
        // business logic methods are called — the stub is here for completeness.
    }
}

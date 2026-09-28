package com.itheima.multiplayer;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EchoIntegrationTest {
    @LocalServerPort private int port;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private URI endpoint() { return URI.create("ws://127.0.0.1:" + port + "/ws/echo"); }

    @Test
    void servesTheBrowserClient() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("WebSocket Echo"));
    }

    @Test
    void preservesTextUnicodeWhitespaceAndMarkup() throws Exception {
        try (Client client = connect()) {
            for (String text : new String[]{"hello", " 中文消息 \n第二行", "", "<img src=x onerror=alert(1)>", "x".repeat(4096)}) {
                client.socket.sendText(text, true).get(5, TimeUnit.SECONDS);
                assertEquals(text, client.messages.poll(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void reassemblesFragmentedText() throws Exception {
        try (Client client = connect()) {
            client.socket.sendText("first ", false).get(5, TimeUnit.SECONDS);
            client.socket.sendText("second", true).get(5, TimeUnit.SECONDS);
            assertEquals("first second", client.messages.poll(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void isolatesConcurrentClientsAndPreservesTheirOrder() throws Exception {
        try (Client first = connect(); Client second = connect()) {
            for (int i = 0; i < 20; i++) {
                CompletableFuture.allOf(first.socket.sendText("A-" + i, true), second.socket.sendText("B-" + i, true)).get(5, TimeUnit.SECONDS);
            }
            for (int i = 0; i < 20; i++) {
                assertEquals("A-" + i, first.messages.poll(5, TimeUnit.SECONDS));
                assertEquals("B-" + i, second.messages.poll(5, TimeUnit.SECONDS));
            }
            assertNull(first.messages.poll(100, TimeUnit.MILLISECONDS));
            assertNull(second.messages.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void rejectsOversizedUtf8MessagesWith1009() throws Exception {
        try (Client client = connect()) {
            client.socket.sendText("汉".repeat(1366), true).get(5, TimeUnit.SECONDS);
            assertEquals(1009, client.closed.get(5, TimeUnit.SECONDS));
            assertTrue(client.messages.isEmpty());
        }
    }

    @Test
    void rejectsForeignBrowserOrigins() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> http.newWebSocketBuilder()
                .header("Origin", "https://untrusted.example")
                .buildAsync(endpoint(), new Client()).get(5, TimeUnit.SECONDS));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, exception.getCause());
        assertEquals(403, handshake.getResponse().statusCode());
    }

    @Test
    void acceptsANewConnectionAfterClosing() throws Exception {
        try (Client first = connect()) {
            first.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            assertEquals(1000, first.closed.get(5, TimeUnit.SECONDS));
        }
        try (Client second = connect()) {
            second.socket.sendText("reconnected", true).get(5, TimeUnit.SECONDS);
            assertEquals("reconnected", second.messages.poll(5, TimeUnit.SECONDS));
        }
    }

    private Client connect() throws Exception {
        Client client = new Client();
        client.socket = http.newWebSocketBuilder().buildAsync(endpoint(), client).get(5, TimeUnit.SECONDS);
        return client;
    }

    private static final class Client implements WebSocket.Listener, AutoCloseable {
        private WebSocket socket;
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();

        @Override public void onOpen(WebSocket socket) { socket.request(1); }

        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) { messages.add(partial.toString()); partial.setLength(0); }
            socket.request(1);
            return null;
        }

        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code);
            return null;
        }

        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
        @Override public void close() { if (socket != null) socket.abort(); }
    }
}

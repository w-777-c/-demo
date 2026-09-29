package com.itheima.multiplayer;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "arena.duel.reconnect-millis=2000")
/** 联机对战集成测试：通过真实 WebSocket 客户端验证服务端权威状态和重连协议。 */
class DuelIntegrationTest {
    @LocalServerPort int port;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    URI endpoint() { return URI.create("ws://127.0.0.1:" + port + "/ws/duel"); }

    @Test void broadcastsAuthoritativeCombatAndRejectsSpectatorAndAttributeSpoofing() throws Exception {
        try (Client a = connect(""); Client b = connect(""); Client observer = connect("")) {
            start(a, b);
            observer.awaitState(s -> s.path("phase").asText().equals("FIGHTING"));
            JsonNode rejected = observer.command("action", Map.of("action", "ATTACK", "seat", 0));
            assertEquals("NOT_SEATED", rejected.path("code").asText());
            assertEquals("ARENA_FULL", observer.command("join", Map.of("name", "第三人")).path("code").asText());
            a.command("action", Map.of("action", "ATTACK", "damage", 999999, "hp", 999999));
            b.awaitState(s -> s.path("turn").asInt() == 2);
            observer.awaitState(s -> s.path("turn").asInt() == 2);
            assertEquals(152, a.state.path("fighters").get(1).path("hp").asInt());
            assertEquals(a.state.path("fighters"), b.state.path("fighters"));
            assertEquals(a.state.path("fighters"), observer.state.path("fighters"));
            assertFalse(observer.state.toString().contains(a.token));
            assertEquals("NOT_YOUR_TURN", a.command("action", Map.of("action", "POTION", "turn", 2)).path("code").asText());
        }
    }

    @Test void concurrentClientsAndDuplicateRequestsCannotAdvanceTheTurnTwice() throws Exception {
        try (Client a = connect(""); Client b = connect("")) {
            start(a, b);
            Map<String, Object> first = a.operation("action", "sim-a", Map.of("action", "ATTACK"));
            Map<String, Object> other = b.operation("action", "sim-b", Map.of("action", "ATTACK"));
            CompletableFuture.allOf(a.send(first), b.send(other)).get(5, TimeUnit.SECONDS);
            assertEquals("ack", a.take(e -> e.path("opId").asText().equals("sim-a")).path("type").asText());
            String code = b.take(e -> e.path("opId").asText().equals("sim-b")).path("code").asText();
            assertTrue(code.equals("NOT_YOUR_TURN") || code.equals("STALE_TURN"));
            a.send(first).get(5, TimeUnit.SECONDS);
            a.take(e -> e.path("type").asText().equals("ack") && e.path("opId").asText().equals("sim-a"));
            assertEquals(2, a.state.path("turn").asInt());
            assertEquals(152, a.state.path("fighters").get(1).path("hp").asInt());
            assertEquals(180, a.state.path("fighters").get(0).path("hp").asInt());
        }
    }

    @Test void resignationAndRematchRejectDelayedCommandsFromThePreviousMatch() throws Exception {
        try (Client a = connect(""); Client b = connect("")) {
            start(a, b);
            String oldMatch = a.state.path("matchId").asText();
            b.command("resign", Map.of());
            a.awaitState(s -> s.path("phase").asText().equals("FINISHED"));
            assertEquals(0, a.state.path("winner").asInt());
            assertEquals("NOT_FIGHTING", b.command("action", Map.of("action", "ATTACK")).path("code").asText());
            a.command("rematch", Map.of()); b.command("rematch", Map.of());
            a.awaitState(s -> !s.path("matchId").asText().equals(oldMatch));
            assertEquals(1, a.state.path("activeSeat").asInt());
            assertEquals("STALE_MATCH", a.command("resign", Map.of("matchId", oldMatch)).path("code").asText());
            assertEquals("FIGHTING", a.state.path("phase").asText());
        }
    }

    @Test void restoresDisconnectedSeatAndReplacesItsOldSocketWithoutLeakingTheToken() throws Exception {
        try (Client a = connect(""); Client b = connect("")) {
            start(a, b);
            a.command("action", Map.of("action", "ATTACK"));
            a.abort();
            b.awaitState(s -> s.path("phase").asText().equals("PAUSED"));
            assertEquals("NOT_FIGHTING", b.command("action", Map.of("action", "ATTACK")).path("code").asText());
            try (Client restored = connect(a.token)) {
                assertEquals(0, restored.seat);
                assertEquals(2, restored.state.path("turn").asInt());
                assertEquals(152, restored.state.path("fighters").get(1).path("hp").asInt());
                try (Client replacement = connect(a.token)) {
                    assertEquals(4001, restored.closed.get(5, TimeUnit.SECONDS));
                    assertEquals(0, replacement.seat);
                    assertEquals("FIGHTING", replacement.state.path("phase").asText());
                }
            }
        }
    }

    @Test void expiresDisconnectedSeatsAndKeepsMalformedMessagesOutOfState() throws Exception {
        try (Client a = connect(""); Client b = connect("")) {
            assertEquals("BAD_NAME", a.command("join", Map.of("name", "<script>")).path("code").asText());
            start(a, b);
            a.socket.sendText("{", true).get(5, TimeUnit.SECONDS);
            assertEquals("BAD_MESSAGE", a.take(e -> e.path("type").asText().equals("error")).path("code").asText());
            a.abort();
            b.awaitState(s -> s.path("phase").asText().equals("FINISHED"));
            assertEquals(1, b.state.path("winner").asInt());
            try (Client expired = connect(a.token)) {
                assertEquals(-1, expired.seat);
                assertEquals("SEAT_EXPIRED", expired.welcome.path("resumeError").asText());
                expired.command("join", Map.of("name", "新人"));
                assertEquals("WAITING", expired.state.path("phase").asText());
            }
        }
    }

    @Test void rejectsCrossOriginBinaryAndOversizedMessages() throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> http.newWebSocketBuilder()
                .header("Origin", "https://foreign.example").buildAsync(endpoint(), new Client()).get(5, TimeUnit.SECONDS));
        assertEquals(403, assertInstanceOf(WebSocketHandshakeException.class, failure.getCause()).getResponse().statusCode());
        try (Client client = connect("")) {
            client.socket.sendBinary(ByteBuffer.wrap(new byte[]{1}), true).get(5, TimeUnit.SECONDS);
            assertEquals(1003, client.closed.get(5, TimeUnit.SECONDS));
        }
        try (Client client = connect("")) {
            client.socket.sendText("x".repeat(4097), true).get(5, TimeUnit.SECONDS);
            assertEquals(1009, client.closed.get(5, TimeUnit.SECONDS));
        }
    }

    void start(Client a, Client b) throws Exception {
        a.command("join", Map.of("name", "青锋")); b.command("join", Map.of("name", "赤刃"));
        a.command("ready", Map.of()); b.command("ready", Map.of());
        a.awaitState(s -> s.path("phase").asText().equals("FIGHTING"));
        assertEquals("FIGHTING", b.state.path("phase").asText());
    }
    Client connect(String token) throws Exception {
        Client client = new Client();
        client.socket = http.newWebSocketBuilder().buildAsync(endpoint(), client).get(5, TimeUnit.SECONDS);
        client.send(Map.of("v", 1, "type", "hello", "resumeToken", token)).get(5, TimeUnit.SECONDS);
        client.welcome = client.take(e -> e.path("type").asText().equals("welcome"));
        return client;
    }
    private class Client implements WebSocket.Listener, AutoCloseable {
        WebSocket socket;
        JsonNode state, welcome;
        String token = "";
        int seat = -1, counter;
        boolean aborted;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        CompletableFuture<WebSocket> send(Object value) throws Exception { return socket.sendText(json.writeValueAsString(value), true); }
        Map<String, Object> operation(String type, String id, Map<String, Object> extra) {
            Map<String, Object> request = new HashMap<>(Map.of("v", 1, "type", type, "opId", id, "matchId", state.path("matchId").asText(), "turn", state.path("turn").asInt()));
            request.putAll(extra); return request;
        }
        JsonNode command(String type, Map<String, Object> extra) throws Exception {
            String id = "op" + ++counter;
            send(operation(type, id, extra)).get(5, TimeUnit.SECONDS);
            return take(e -> e.path("opId").asText().equals(id));
        }
        void awaitState(Predicate<JsonNode> predicate) throws Exception {
            if (!predicate.test(state)) take(e -> e.has("state") && predicate.test(e.path("state")));
        }
        JsonNode take(Predicate<JsonNode> predicate) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < end) {
                String value = messages.poll(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
                assertNotNull(value, "Expected duel event");
                JsonNode event = json.readTree(value);
                if (event.has("state")) state = event.path("state");
                if (event.path("type").asText().equals("welcome")) { token = event.path("resumeToken").asText(); seat = event.path("yourSeat").asInt(); }
                if (predicate.test(event)) return event;
            }
            throw new AssertionError("Timed out waiting for duel event");
        }
        void abort() { aborted = true; socket.abort(); }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text); if (last) { messages.add(partial.toString()); partial.setLength(0); } socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) { closed.complete(code); return null; }
        @Override public void onError(WebSocket socket, Throwable failure) { closed.completeExceptionally(failure); }
        @Override public void close() throws Exception {
            if (aborted || closed.isDone()) return;
            if (seat >= 0) command("leave", Map.of());
            if (!closed.isDone()) socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
            closed.get(5, TimeUnit.SECONDS);
        }
    }
}

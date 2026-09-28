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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoardIntegrationTest {
    @LocalServerPort private int port;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private URI endpoint() { return URI.create("ws://127.0.0.1:" + port + "/ws/board"); }

    @BeforeEach void resetBoard() throws Exception {
        try (Client client = connect()) {
            client.send(Map.of("v", 1, "type", "clear", "opId", "reset", "epoch", client.initial.path("epoch").asLong()));
            client.take("clear");
        }
    }

    @Test void broadcastsDrawingAndRestoresTheSameSnapshotForNewClients() throws Exception {
        try (Client first = connect(); Client second = connect()) {
            first.send(draw("one", first.initial.path("epoch").asLong()));
            JsonNode event = first.take("draw");
            assertEquals(event, second.take("draw"));
            assertEquals(first.initial.path("clientId"), event.path("segment").path("clientId"));
            assertEquals(1, event.path("segment").path("points").size());
            try (Client late = connect()) {
                assertEquals(event.path("seq"), late.initial.path("seq"));
                assertEquals(event.path("segment"), late.initial.path("segments").get(0));
                assertEquals(3, late.take("presence").path("count").asInt());
            }
            JsonNode presence;
            do { presence = first.take("presence"); } while (presence.path("count").asInt() != 2);
        }
    }

    @Test void concurrentWritersAndMidStreamJoinObserveOneGlobalOrder() throws Exception {
        try (Client first = connect(); Client second = connect()) {
            long epoch = first.initial.path("epoch").asLong();
            long baseline = first.initial.path("seq").asLong();
            CompletableFuture<Void> a = CompletableFuture.runAsync(() -> writeMany(first, "a", epoch));
            CompletableFuture<Void> b = CompletableFuture.runAsync(() -> writeMany(second, "b", epoch));
            try (Client joining = connect()) {
                CompletableFuture.allOf(a, b).get(10, TimeUnit.SECONDS);
                List<JsonNode> events = new ArrayList<>();
                for (int i = 0; i < 40; i++) {
                    JsonNode event = first.take("draw");
                    assertEquals(baseline + i + 1, event.path("seq").asLong());
                    assertEquals(event, second.take("draw"));
                    events.add(event);
                }
                int snapshotSize = joining.initial.path("segments").size();
                assertEquals(baseline + snapshotSize, joining.initial.path("seq").asLong());
                for (int i = 0; i < snapshotSize; i++) assertEquals(events.get(i).path("segment"), joining.initial.path("segments").get(i));
                for (int i = snapshotSize; i < 40; i++) assertEquals(events.get(i), joining.take("draw"));
            }
        }
    }

    private void writeMany(Client client, String prefix, long epoch) {
        try { for (int i = 0; i < 20; i++) client.send(draw(prefix + i, epoch)); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    @Test void duplicateOperationsDoNotDrawTwiceOrClearNewerInk() throws Exception {
        try (Client client = connect()) {
            long epoch = client.initial.path("epoch").asLong();
            client.send(draw("draw", epoch));
            long revision = client.take("draw").path("seq").asLong();
            client.send(draw("draw", epoch));
            assertEquals(revision, client.take("ack").path("seq").asLong());
            client.send(Map.of("v", 1, "type", "clear", "opId", "clear", "epoch", epoch));
            JsonNode cleared = client.take("clear");
            client.send(draw("new-draw", cleared.path("epoch").asLong()));
            client.take("draw");
            client.send(Map.of("v", 1, "type", "clear", "opId", "clear", "epoch", epoch));
            client.take("ack");
            client.send(Map.of("v", 1, "type", "sync"));
            assertEquals(1, client.take("snapshot").path("segments").size());
        }
    }

    @Test void clearingRejectsLateStrokesFromThePreviousEpoch() throws Exception {
        try (Client first = connect(); Client second = connect()) {
            long oldEpoch = first.initial.path("epoch").asLong();
            first.send(Map.of("v", 1, "type", "clear", "opId", "clear", "epoch", oldEpoch));
            JsonNode cleared = first.take("clear");
            assertEquals(cleared, second.take("clear"));
            second.send(draw("late", oldEpoch));
            assertEquals("STALE_EPOCH", second.take("error").path("code").asText());
            JsonNode snapshot = second.take("snapshot");
            assertEquals(cleared.path("seq"), snapshot.path("seq"));
            assertTrue(snapshot.path("segments").isEmpty());
            second.send(draw("fresh", cleared.path("epoch").asLong()));
            assertEquals(first.take("draw"), second.take("draw"));
        }
    }

    @Test void invalidMessagesDoNotMutateTheBoardAndConnectionRemainsUsable() throws Exception {
        try (Client client = connect()) {
            for (String invalid : List.of("{", "[]", "null", "{\"v\":2,\"type\":\"sync\"}", "{\"v\":1,\"type\":\"unknown\"}")) {
                client.socket.sendText(invalid, true).get(5, TimeUnit.SECONDS);
                assertEquals("BAD_MESSAGE", client.take("error").path("code").asText());
            }
            for (Object points : List.of(List.of(List.of(-1, 2)), List.of(List.of(1201, 2)), List.of(List.of(2)), List.of())) {
                Map<String, Object> invalid = new java.util.HashMap<>(draw("bad", client.initial.path("epoch").asLong()));
                invalid.put("points", points);
                client.send(invalid);
                assertEquals("BAD_MESSAGE", client.take("error").path("code").asText());
            }
            client.send(Map.of("v", 1, "type", "sync"));
            assertEquals(client.initial.path("seq"), client.take("snapshot").path("seq"));
            client.send(Map.of("v", 1, "type", "ping"));
            assertEquals("pong", client.take("pong").path("type").asText());
        }
    }

    @Test void boundsStoredPointsAndCanClearAfterReachingCapacity() throws Exception {
        try (Client client = connect()) {
            long epoch = client.initial.path("epoch").asLong();
            for (int i = 0; i < BoardHandler.MAX_POINTS / 32; i++) {
                Map<String, Object> draw = new java.util.HashMap<>(draw("fill" + i, epoch));
                draw.put("points", java.util.Collections.nCopies(32, List.of(1200, 720)));
                client.send(draw);
                client.take("draw");
            }
            client.send(draw("overflow", epoch));
            assertEquals("BOARD_FULL", client.take("error").path("code").asText());
            try (Client late = connect()) { assertEquals(BoardHandler.MAX_POINTS / 32, late.initial.path("segments").size()); }
            client.send(Map.of("v", 1, "type", "clear", "opId", "clear", "epoch", epoch));
            client.send(draw("after-clear", client.take("clear").path("epoch").asLong()));
            client.take("draw");
        }
    }

    @Test void rejectsForeignOriginsAndUnsupportedFrames() throws Exception {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> http.newWebSocketBuilder()
                .header("Origin", "https://foreign.example").buildAsync(endpoint(), new Client()).get(5, TimeUnit.SECONDS));
        assertEquals(403, assertInstanceOf(WebSocketHandshakeException.class, exception.getCause()).getResponse().statusCode());
        try (Client client = connect()) {
            client.socket.sendBinary(ByteBuffer.wrap(new byte[]{1}), true).get(5, TimeUnit.SECONDS);
            assertEquals(1003, client.closed.get(5, TimeUnit.SECONDS));
        }
        try (Client client = connect()) {
            client.socket.sendText("x".repeat(8193), true).get(5, TimeUnit.SECONDS);
            assertEquals(1009, client.closed.get(5, TimeUnit.SECONDS));
        }
    }

    private Map<String, Object> draw(String id, long epoch) {
        return Map.of("v", 1, "type", "draw", "opId", id, "epoch", epoch, "tool", "pen", "color", "#148579", "width", 6, "points", List.of(List.of(100, 100)));
    }

    private Client connect() throws Exception {
        Client client = new Client();
        client.socket = http.newWebSocketBuilder().buildAsync(endpoint(), client).get(5, TimeUnit.SECONDS);
        client.initial = client.take("snapshot");
        return client;
    }

    private class Client implements WebSocket.Listener, AutoCloseable {
        WebSocket socket;
        JsonNode initial;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        void send(Object event) throws Exception { socket.sendText(json.writeValueAsString(event), true).get(5, TimeUnit.SECONDS); }
        JsonNode take(String type) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String message = messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                assertNotNull(message, "Waiting for " + type);
                JsonNode event = json.readTree(message);
                if (event.path("type").asText().equals(type)) return event;
                assertEquals("presence", event.path("type").asText(), "Unexpected event while waiting for " + type + ": " + event);
            }
            throw new AssertionError("Timed out waiting for " + type);
        }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) { messages.add(partial.toString()); partial.setLength(0); }
            socket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) { closed.complete(code); return null; }
        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
        @Override public void close() throws Exception {
            if (!closed.isDone() && !socket.isOutputClosed()) socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
            closed.get(5, TimeUnit.SECONDS);
        }
    }
}

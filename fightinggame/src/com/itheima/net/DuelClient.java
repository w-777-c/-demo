package com.itheima.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** All mutable client state and UI callbacks belong to the Swing event thread. */
/** 桌面联机客户端：维护 WebSocket、自动重连、恢复凭据和最新权威状态快照。 */
public final class DuelClient implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final URI endpoint;
    private final Consumer<DuelClient> changed;
    private final Timer timer;
    private WebSocket socket;
    private CompletableFuture<WebSocket> sends;
    private JsonNode state = JSON.createObjectNode();
    private String token = "", pending = "", status = "连接中", error = "";
    private int seat = -1, generation, attempts;
    private boolean closed, welcomed;
    private long lastReceived, lastPing, pendingSince, retryAt;

    public DuelClient(URI endpoint, Consumer<DuelClient> changed) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT required");
        this.endpoint = endpoint; this.changed = changed;
        timer = new Timer(1000, event -> tick());
        timer.start(); connect();
    }
    /** 将用户输入的主机地址规范化为服务端 duel WebSocket 端点。 */
    public static URI endpoint(String address) {
        String value = address.strip();
        if (!value.contains("://")) value = "ws://" + value;
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        scheme = switch (scheme) { case "http" -> "ws"; case "https" -> "wss"; default -> scheme; };
        if (!(scheme.equals("ws") || scheme.equals("wss")) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/") || uri.getPath().equals("/ws/duel"))) {
            throw new IllegalArgumentException("请输入主机地址，例如 192.168.1.20:8765");
        }
        try { return new URI(scheme, null, uri.getHost(), uri.getPort(), "/ws/duel", null, null); }
        catch (java.net.URISyntaxException invalid) { throw new IllegalArgumentException("地址无效", invalid); }
    }
    public JsonNode state() { return state; }
    public int seat() { return seat; }
    public boolean available() { return welcomed && !closed && pending.isEmpty(); }
    public String status() { return status; }
    public String error() { return error; }

    private void connect() {
        int attempt = ++generation;
        status = attempts == 0 ? "连接中" : "正在重连";
        retryAt = 0; changed.accept(this);
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).buildAsync(endpoint, new WebSocket.Listener() {
            private final StringBuilder parts = new StringBuilder();
            @Override public void onOpen(WebSocket webSocket) {
                SwingUtilities.invokeLater(() -> {
                    if (closed || generation != attempt) { webSocket.abort(); return; }
                    socket = webSocket; sends = CompletableFuture.completedFuture(socket);
                    lastReceived = lastPing = System.nanoTime();
                    send(message("hello").put("resumeToken", token));
                });
                webSocket.request(1);
            }
            @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                if (parts.length() + data.length() > 262144) {
                    webSocket.abort(); failed(attempt); return null;
                }
                parts.append(data);
                if (last) {
                    String text = parts.toString(); parts.setLength(0);
                    SwingUtilities.invokeLater(() -> receive(attempt, text));
                }
                webSocket.request(1); return null;
            }
            @Override public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
                SwingUtilities.invokeLater(() -> {
                    if (generation != attempt || closed) return;
                    if (code == 4001) {
                        token = ""; seat = -1; welcomed = false; closed = true; timer.stop();
                        status = "席位已在其他连接恢复"; changed.accept(DuelClient.this);
                    } else retry(attempt);
                });
                return null;
            }
            @Override public void onError(WebSocket webSocket, Throwable failure) { failed(attempt); }
        }).whenComplete((webSocket, failure) -> { if (failure != null) failed(attempt); });
    }
    private void failed(int attempt) { SwingUtilities.invokeLater(() -> retry(attempt)); }
    private void retry(int attempt) {
        if (closed || attempt != generation) return;
        generation++; welcomed = false; pending = "";
        if (socket != null) socket.abort();
        socket = null;
        retryAt = System.nanoTime() + Math.min(8, 1 << Math.min(attempts++, 3)) * 1_000_000_000L;
        status = "连接中断，正在重试"; changed.accept(this);
    }
    private void tick() {
        long now = System.nanoTime();
        if (retryAt != 0 && now >= retryAt) { connect(); return; }
        if (socket == null) return;
        if (now - lastReceived > 65_000_000_000L || !pending.isEmpty() && now - pendingSince > 8_000_000_000L) {
            retry(generation); return;
        }
        if (welcomed && now - lastPing > 25_000_000_000L) { lastPing = now; send(message("ping")); }
    }
    private void receive(int attempt, String text) {
        if (closed || attempt != generation) return;
        try {
            JsonNode envelope = JSON.readTree(text);
            if (envelope == null || envelope.path("v").asInt() != 1) throw new IllegalArgumentException("Invalid message");
            lastReceived = System.nanoTime();
            String type = envelope.path("type").asText();
            if (type.equals("welcome")) {
                seat = envelope.path("yourSeat").asInt(-1); token = envelope.path("resumeToken").asText("");
                welcomed = true; attempts = 0; status = "已连接";
                error = envelope.path("resumeError").asText().isEmpty() ? "" : "席位已过期，请重新入席。";
            }
            if (envelope.path("state").isObject()) state = envelope.path("state");
            if (type.equals("error")) error = describe(envelope.path("code").asText());
            if ((type.equals("ack") || type.equals("error")) && envelope.path("opId").asText().equals(pending)) pending = "";
            changed.accept(this);
        } catch (Exception invalid) { retry(attempt); }
    }
    /** 向服务端发送加入、准备、行动、认输等协议命令。 */
    public void command(String type, String value) {
        if (!available()) return;
        ObjectNode command = message(type).put("opId", UUID.randomUUID().toString())
                .put("matchId", state.path("matchId").asText()).put("turn", state.path("turn").asLong());
        if (type.equals("join")) command.put("name", value);
        if (type.equals("action")) command.put("action", value);
        pending = command.path("opId").asText(); pendingSince = System.nanoTime(); error = "";
        send(command); changed.accept(this);
    }
    private ObjectNode message(String type) { return JSON.createObjectNode().put("v", 1).put("type", type); }
    private void send(ObjectNode message) {
        if (socket == null || closed) return;
        int attempt = generation;
        sends = sends.thenCompose(ws -> ws.sendText(message.toString(), true));
        sends.whenComplete((ws, failure) -> { if (failure != null) failed(attempt); });
    }
    @Override public void close() {
        if (welcomed && seat >= 0) send(message("leave").put("opId", UUID.randomUUID().toString()));
        closed = true; welcomed = false; generation++; timer.stop();
        WebSocket closing = socket;
        if (closing != null) sends.thenCompose(ws -> ws.sendClose(1000, "Leaving arena"))
                .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).whenComplete((ws, error) -> closing.abort());
    }
    private static String describe(String code) {
        return switch (code) {
            case "ARENA_FULL" -> "席位已满，可继续观战。";
            case "BAD_NAME" -> "昵称须为1至16位文字、数字、空格、下划线或连字符。";
            case "NOT_YOUR_TURN", "STALE_TURN" -> "回合已变化，请按最新状态出招。";
            case "STALE_MATCH" -> "对局已更新，请重新操作。";
            case "SKILL_UNAVAILABLE" -> "当前技能不可用。";
            case "NOT_SEATED" -> "请先入席。";
            default -> "操作未生效（" + code + "）。";
        };
    }
}

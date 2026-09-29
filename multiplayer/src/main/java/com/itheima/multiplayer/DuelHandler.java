package com.itheima.multiplayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.game.Battle.Action;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 联机回合对战的 WebSocket 入口。
 *
 * <p>连接成员表和 DuelArena 使用同一把锁，所有命令执行完成后广播权威快照；
 * 通过 opId 记录已处理命令，保证客户端重试不会重复扣血或推进回合。</p>
 */
@Component
public final class DuelHandler extends TextWebSocketHandler {
    /** 串行化连接生命周期、命令处理和竞技场状态变更。 */
    private final Object lock = new Object();
    private final ObjectMapper json;
    private final DuelArena arena;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private boolean stopped;
    private static final class Member {
        final SessionSender sender;
        final LinkedHashMap<String, Long> accepted = new LinkedHashMap<>();
        boolean initialized;
        Member(SessionSender sender) { this.sender = sender; }
    }
    public DuelHandler(ObjectMapper json, @Value("${arena.duel.reconnect-millis:30000}") long graceMillis) {
        this.json = json;
        this.arena = new DuelArena(Clock.systemUTC(), graceMillis);
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 连接先进入发送队列体系，再等待客户端发送 hello 进行身份初始化。
        SessionSender sender = null;
        // 在锁内完成幂等判断、权威校验和广播，确保并发命令不会交错。
        synchronized (lock) {
            if (!stopped && members.size() < 24) {
                sender = new SessionSender(session, () -> remove(session.getId()));
                members.put(session.getId(), new Member(sender));
            }
        }
        if (sender == null) session.close(new CloseStatus(1013, "Arena connections full"));
        else sender.start();
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage text) throws Exception {
        if (text.getPayload().getBytes(StandardCharsets.UTF_8).length > 4096) {
            session.close(new CloseStatus(1009, "Message exceeds 4096 UTF-8 bytes"));
            return;
        }
        JsonNode request;
        try { request = json.readTree(text.getPayload()); }
        catch (JsonProcessingException malformed) { request = null; }
        synchronized (lock) {
            Member member = members.get(session.getId());
            if (member == null) return;
            expire();
            String opId = "";
            try {
                require(request != null && request.isObject() && request.path("v").isInt() && request.path("v").intValue() == 1, "BAD_MESSAGE");
                String type = request.path("type").asText();
                if (type.equals("hello")) {
                    require(!member.initialized, "ALREADY_CONNECTED");
                    String token = request.path("resumeToken").asText("");
                    require(token.length() <= 64, "BAD_MESSAGE");
                    member.initialized = true;
                    DuelArena.Seat seat = null;
                    String resumeError = "";
                    if (!token.isEmpty()) {
                        try { seat = arena.resume(session.getId(), token); }
                        catch (DuelArena.RuleViolation failure) { resumeError = failure.code; }
                    }
                    if (seat != null && !seat.replacedConnection().isEmpty()) {
                        Member previous = members.get(seat.replacedConnection());
                        if (previous != null) previous.sender.stop(new CloseStatus(4001, "Seat resumed in another connection"));
                    }
                    welcome(member, seat, resumeError);
                    if (seat != null) broadcast();
                    return;
                }
                require(member.initialized, "HELLO_REQUIRED");
                if (type.equals("ping")) { send(member, Map.of("v", 1, "type", "pong")); return; }
                if (type.equals("sync")) { sendState(member); return; }
                opId = request.path("opId").asText();
                require(request.path("opId").isTextual() && opId.matches("[A-Za-z0-9_-]{1,64}"), "BAD_MESSAGE");
                if (member.accepted.containsKey(opId)) {
                    sendState(member);
                    ack(member, opId, member.accepted.get(opId));
                    return;
                }
                if (!type.equals("join") && !type.equals("leave")) arena.checkMatch(request.path("matchId").asText());
                switch (type) {
                    case "join" -> {
                        require(request.path("name").isTextual(), "BAD_NAME");
                        welcome(member, arena.join(session.getId(), request.path("name").textValue()), "");
                    }
                    case "ready" -> arena.ready(session.getId());
                    case "action" -> {
                        require(request.path("turn").isIntegralNumber() && request.path("turn").canConvertToLong(), "BAD_MESSAGE");
                        Action action;
                        try { action = Action.valueOf(request.path("action").asText()); }
                        catch (IllegalArgumentException invalid) { throw new DuelArena.RuleViolation("BAD_ACTION"); }
                        arena.action(session.getId(), request.path("turn").longValue(), action);
                    }
                    case "resign" -> arena.resign(session.getId());
                    case "rematch" -> arena.rematch(session.getId());
                    case "leave" -> { arena.leave(session.getId()); welcome(member, null, ""); }
                    default -> throw new DuelArena.RuleViolation("BAD_MESSAGE");
                }
                member.accepted.put(opId, arena.revision());
                if (member.accepted.size() > 256) member.accepted.remove(member.accepted.keySet().iterator().next());
                broadcast();
                ack(member, opId, arena.revision());
            } catch (DuelArena.RuleViolation invalid) {
                send(member, Map.of("v", 1, "type", "error", "code", invalid.code, "opId", opId, "state", arena.snapshot()));
            }
        }
    }
    private static void require(boolean condition, String code) { if (!condition) throw new DuelArena.RuleViolation(code); }
    private void ack(Member member, String opId, long revision) { send(member, Map.of("v", 1, "type", "ack", "opId", opId, "revision", revision)); }
    private void welcome(Member member, DuelArena.Seat seat, String error) {
        // welcome 同时返回当前快照和重连令牌，观战者的席位为 -1。
        send(member, Map.of("v", 1, "type", "welcome", "yourSeat", seat == null ? -1 : seat.seat(),
                "resumeToken", seat == null ? "" : seat.token(), "resumeError", error, "state", arena.snapshot()));
    }
    private TextMessage encode(Object value) {
        try { return new TextMessage(json.writeValueAsBytes(value)); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot encode duel state", failure); }
    }
    private void send(Member member, Object value) { member.sender.offer(encode(value)); }
    private void sendState(Member member) { send(member, Map.of("v", 1, "type", "state", "state", arena.snapshot())); }
    private void broadcast() {
        // 只向完成 hello 的连接推送统一状态，未初始化连接仍可继续发送 hello。
        TextMessage message = encode(Map.of("v", 1, "type", "state", "state", arena.snapshot()));
        for (Member member : members.values()) if (member.initialized) member.sender.offer(message);
    }
    private void expire() {
        long before = arena.revision();
        arena.expire();
        if (before != arena.revision()) broadcast();
    }
    /** 每 500ms 检查一次断线席位是否超过重连宽限期。 */
    @Scheduled(fixedDelay = 500) public void expireDisconnectedSeats() { synchronized (lock) { if (!stopped) expire(); } }
    private void remove(String id) {
        synchronized (lock) {
            Member member = members.remove(id);
            if (member == null) return;
            member.sender.stop(CloseStatus.NORMAL);
            long before = arena.revision();
            arena.disconnect(id);
            if (before != arena.revision()) broadcast();
        }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable failure) { remove(session.getId()); }
    /** 应用关闭时停止发送线程，避免后台线程持有已销毁的 WebSocket。 */
    @PreDestroy public void shutdown() {
        synchronized (lock) {
            stopped = true;
            for (Member member : members.values()) member.sender.stop(CloseStatus.GOING_AWAY);
            members.clear();
        }
    }
}

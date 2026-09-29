package com.itheima.multiplayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 多用户同步画板的 WebSocket 处理器。
 *
 * <p>所有画板状态都在 {@link #lock} 保护下修改，操作按服务端接收顺序分配序号，
 * 客户端可以通过序号和 epoch 检测丢包、乱序以及清空后的旧操作。</p>
 */
@Component
public final class BoardHandler extends TextWebSocketHandler {
    static final int WIDTH = 1200;
    static final int HEIGHT = 720;
    static final int MAX_CLIENTS = 24;
    static final int MAX_SEGMENTS = 2000;
    static final int MAX_POINTS = 20000;
    /** 保护成员列表、画线数据、序号和清空代次的一把状态锁。 */
    private final Object lock = new Object();
    private final ObjectMapper json;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final List<Segment> segments = new ArrayList<>();
    private long seq;
    private long epoch;
    private int pointCount;

    public BoardHandler(ObjectMapper json) { this.json = json; }

    record Segment(String opId, String clientId, String tool, String color, int width, List<List<Double>> points) {}
    private static final class Member {
        final String id = UUID.randomUUID().toString();
        final SessionSender client;
        final LinkedHashMap<String, Long> accepted = new LinkedHashMap<>();
        Member(SessionSender client) { this.client = client; }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 先在状态锁内登记连接并发送快照，再启动发送线程，避免遗漏初始状态。
        SessionSender client = null;
        synchronized (lock) {
            if (members.size() < MAX_CLIENTS) {
                client = new SessionSender(session, () -> remove(session.getId()));
                Member member = new Member(client);
                members.put(session.getId(), member);
                snapshot(member);
                presence();
            }
        }
        if (client == null) session.close(new CloseStatus(1013, "Board is full"));
        else client.start();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > 8192) {
            session.close(new CloseStatus(1009, "Message exceeds 8192 UTF-8 bytes"));
            return;
        }
        JsonNode request;
        try {
            request = json.readTree(message.getPayload());
        } catch (JsonProcessingException invalidJson) {
            request = null;
        }
        // 状态变更、序号分配、快照生成和发送队列入队必须共享同一顺序。
        synchronized (lock) {
            Member member = members.get(session.getId());
            if (member == null) return;
            try {
                require(request != null && request.isObject() && request.path("v").isInt() && request.path("v").intValue() == 1);
                String type = request.path("type").asText();
                if (type.equals("ping")) { send(member, Map.of("v", 1, "type", "pong")); return; }
                if (type.equals("sync")) { snapshot(member); return; }
                require(type.equals("draw") || type.equals("clear"));
                String opId = request.path("opId").asText();
                require(request.path("opId").isTextual() && opId.matches("[A-Za-z0-9_-]{1,64}"));
                JsonNode requestEpoch = request.path("epoch");
                require(requestEpoch.isIntegralNumber() && requestEpoch.canConvertToLong() && requestEpoch.longValue() >= 0);
                if (member.accepted.containsKey(opId)) {
                    send(member, Map.of("v", 1, "type", "ack", "opId", opId, "seq", member.accepted.get(opId)));
                    return;
                }
                if (requestEpoch.longValue() != epoch) {
                    error(member, "STALE_EPOCH");
                    snapshot(member);
                    return;
                }
                Segment segment = null;
                if (type.equals("draw")) {
                    segment = parseSegment(member.id, opId, request);
                    if (segments.size() >= MAX_SEGMENTS || pointCount + segment.points().size() > MAX_POINTS) {
                        error(member, "BOARD_FULL");
                        return;
                    }
                    segments.add(segment);
                    pointCount += segment.points().size();
                } else {
                    segments.clear();
                    pointCount = 0;
                    epoch++;
                }
                seq++;
                member.accepted.put(opId, seq);
                if (member.accepted.size() > 256) member.accepted.remove(member.accepted.keySet().iterator().next());
                if (segment != null) broadcast(Map.of("v", 1, "type", "draw", "seq", seq, "epoch", epoch, "segment", segment));
                else broadcast(Map.of("v", 1, "type", "clear", "seq", seq, "epoch", epoch, "opId", opId, "clientId", member.id));
            } catch (IllegalArgumentException invalidMessage) {
                error(member, "BAD_MESSAGE");
            }
        }
    }

    private Segment parseSegment(String clientId, String opId, JsonNode request) {
        // 严格限制工具、颜色、笔宽和坐标范围，防止异常数据污染共享画布。
        String tool = request.path("tool").asText();
        require(tool.equals("pen") || tool.equals("eraser"));
        String color = request.path("color").asText();
        require(color.matches("#[0-9a-fA-F]{6}"));
        JsonNode width = request.path("width");
        require(width.isInt() && width.intValue() >= 1 && width.intValue() <= 32);
        JsonNode inputPoints = request.path("points");
        require(inputPoints.isArray() && !inputPoints.isEmpty() && inputPoints.size() <= 32);
        List<List<Double>> points = new ArrayList<>();
        for (JsonNode point : inputPoints) {
            require(point.isArray() && point.size() == 2 && point.get(0).isNumber() && point.get(1).isNumber());
            double x = point.get(0).doubleValue(), y = point.get(1).doubleValue();
            require(Double.isFinite(x) && Double.isFinite(y) && x >= 0 && x <= WIDTH && y >= 0 && y <= HEIGHT);
            points.add(List.of(Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0));
        }
        return new Segment(opId, clientId, tool, tool.equals("eraser") ? "#ffffff" : color.toLowerCase(java.util.Locale.ROOT), width.intValue(), List.copyOf(points));
    }

    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid board message"); }

    private void snapshot(Member member) {
        // 快照是客户端断线重连、发现序号缺口或清空代次过期时的权威状态。
        send(member, Map.of("v", 1, "type", "snapshot", "seq", seq, "epoch", epoch, "width", WIDTH, "height", HEIGHT,
                "clientId", member.id, "segments", segments, "maxSegments", MAX_SEGMENTS, "maxPoints", MAX_POINTS));
    }

    private void error(Member member, String code) { send(member, Map.of("v", 1, "type", "error", "code", code)); }
    private void presence() { broadcast(Map.of("v", 1, "type", "presence", "count", members.size())); }
    private void send(Member member, Object event) { member.client.offer(encode(event)); }
    private TextMessage encode(Object event) {
        try { return new TextMessage(json.writeValueAsBytes(event)); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot encode board event", failure); }
    }
    private void broadcast(Object event) {
        // 实际网络写入由每个 SessionSender 的独立线程完成，避免阻塞状态锁。
        TextMessage message = encode(event);
        for (Member member : members.values()) member.client.offer(message);
    }

    private void remove(String sessionId) {
        synchronized (lock) {
            Member removed = members.remove(sessionId);
            if (removed != null) {
                removed.client.stop(CloseStatus.NORMAL);
                presence();
            }
        }
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) { remove(session.getId()); }

    @PreDestroy
    public void shutdown() {
        // Spring 停止应用时释放所有发送线程和 WebSocket 连接。
        synchronized (lock) {
            for (Member member : members.values()) member.client.stop(CloseStatus.GOING_AWAY);
            members.clear();
        }
    }
}

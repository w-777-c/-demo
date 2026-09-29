package com.itheima.multiplayer;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 最小可运行的 WebSocket 回声服务：收到文本后原样返回。 */
@Component
public final class EchoHandler extends TextWebSocketHandler {
    public static final int MAX_MESSAGE_BYTES = 4096;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 以 UTF-8 字节数限制消息，确保中英文输入使用同一套上限。
        if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            session.close(new CloseStatus(1009, "Text message exceeds 4096 UTF-8 bytes"));
            return;
        }
        session.sendMessage(message);
    }
}

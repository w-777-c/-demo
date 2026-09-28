package com.itheima.multiplayer;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public final class EchoHandler extends TextWebSocketHandler {
    public static final int MAX_MESSAGE_BYTES = 4096;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            session.close(new CloseStatus(1009, "Text message exceeds 4096 UTF-8 bytes"));
            return;
        }
        session.sendMessage(message);
    }
}

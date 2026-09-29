package com.itheima.multiplayer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** 注册回声、画板和联机对战三个 WebSocket 端点及容器级限制。 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final EchoHandler echoHandler;
    private final BoardHandler boardHandler;
    private final DuelHandler duelHandler;

    public WebSocketConfig(EchoHandler echoHandler, BoardHandler boardHandler, DuelHandler duelHandler) {
        this.echoHandler = echoHandler;
        this.boardHandler = boardHandler;
        this.duelHandler = duelHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 三个端点共用同一前端静态资源目录，协议和状态由各自处理器维护。
        registry.addHandler(echoHandler, "/ws/echo");
        registry.addHandler(boardHandler, "/ws/board");
        registry.addHandler(duelHandler, "/ws/duel");
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        // 限制单条文本消息和空闲时间，避免异常客户端长期占用连接资源。
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(65536);
        container.setMaxSessionIdleTimeout(120000L);
        return container;
    }
}

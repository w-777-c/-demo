package com.itheima.multiplayer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

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
        registry.addHandler(echoHandler, "/ws/echo");
        registry.addHandler(boardHandler, "/ws/board");
        registry.addHandler(duelHandler, "/ws/duel");
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(65536);
        container.setMaxSessionIdleTimeout(120000L);
        return container;
    }
}

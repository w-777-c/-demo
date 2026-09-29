package com.itheima.multiplayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 联机模块的 Spring Boot 启动入口，同时启用断线席位清理的定时任务。 */
@SpringBootApplication
@EnableScheduling
public class MultiplayerApplication {
    public static void main(String[] args) {
        // 由 Spring Boot 创建 WebSocket 处理器并启动内嵌 HTTP 服务。
        SpringApplication.run(MultiplayerApplication.class, args);
    }
}

package com.itheima.multiplayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MultiplayerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultiplayerApplication.class, args);
    }
}

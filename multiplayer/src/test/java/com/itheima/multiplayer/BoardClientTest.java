package com.itheima.multiplayer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** 画板发送器单元测试，重点验证慢客户端不会阻塞发布线程或无限占用内存。 */
class BoardClientTest {
    @Test void slowClientHasBoundedQueueAndDoesNotBlockThePublisher() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("slow-client");
        when(session.isOpen()).thenReturn(true);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return null;
        }).when(session).sendMessage(any());
        SessionSender client = new SessionSender(session, closed::countDown);
        client.start();
        try {
            client.offer(new TextMessage("first"));
            assertTrue(sending.await(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                for (int i = 0; i < 130; i++) client.offer(new TextMessage("queued"));
            });
            assertTrue(closed.await(3, TimeUnit.SECONDS));
            verify(session).close(new CloseStatus(1013, "Client is too slow; reconnect for a snapshot"));
        } finally {
            release.countDown();
            client.stop(CloseStatus.NORMAL);
        }
    }
}

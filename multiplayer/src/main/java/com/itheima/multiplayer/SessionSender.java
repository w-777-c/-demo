package com.itheima.multiplayer;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 每个连接独立的有界发送队列。
 *
 * <p>状态线程只负责入队，网络写入在后台线程执行；队列或字节数超限时主动断开慢客户端，
 * 让客户端通过快照重新同步，避免内存无限增长。</p>
 */
final class SessionSender {
    private final WebSocketSession session;
    private final Runnable onClosed;
    private final ArrayBlockingQueue<TextMessage> outbound = new ArrayBlockingQueue<>(128);
    private final AtomicInteger queuedBytes = new AtomicInteger();
    private final Thread sender;
    private volatile CloseStatus closing;

    SessionSender(WebSocketSession session, Runnable onClosed) {
        this.session = session;
        this.onClosed = onClosed;
        sender = new Thread(this::sendLoop, "session-sender-" + session.getId());
        sender.setDaemon(true);
    }

    void start() { sender.start(); }

    /** 将消息放入队列；返回路径不执行阻塞式网络 I/O。 */
    void offer(TextMessage message) {
        if (closing != null) return;
        if (queuedBytes.addAndGet(message.getPayloadLength()) > 2 * 1024 * 1024 || !outbound.offer(message)) {
            stop(new CloseStatus(1013, "Client is too slow; reconnect for a snapshot"));
        }
    }

    /** 幂等地标记关闭原因并唤醒发送线程。 */
    synchronized void stop(CloseStatus status) {
        if (closing == null) closing = status;
        sender.interrupt();
    }

    /** 顺序消费发送队列，并在异常或停止后清理底层连接。 */
    private void sendLoop() {
        try {
            while (closing == null) {
                TextMessage message = outbound.take();
                queuedBytes.addAndGet(-message.getPayloadLength());
                if (closing == null) session.sendMessage(message);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failure) {
            stop(CloseStatus.SERVER_ERROR);
        } finally {
            outbound.clear();
            // Clear cancellation before transport cleanup, which may acquire interruptible locks.
            Thread.interrupted();
            try {
                if (session.isOpen()) session.close(closing == null ? CloseStatus.NORMAL : closing);
            } catch (IOException ignored) {
                // The transport may already have closed the connection.
            } finally {
                onClosed.run();
            }
        }
    }
}

package com.itheima.multiplayer;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** One bounded sender per connection keeps socket I/O outside the state lock. */
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

    void offer(TextMessage message) {
        if (closing != null) return;
        if (queuedBytes.addAndGet(message.getPayloadLength()) > 2 * 1024 * 1024 || !outbound.offer(message)) {
            stop(new CloseStatus(1013, "Client is too slow; reconnect for a snapshot"));
        }
    }

    synchronized void stop(CloseStatus status) {
        if (closing == null) closing = status;
        sender.interrupt();
    }

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

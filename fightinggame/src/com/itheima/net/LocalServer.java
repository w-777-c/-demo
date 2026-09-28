package com.itheima.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Owns only the server process started for this desktop lobby. */
public final class LocalServer implements AutoCloseable {
    private volatile Process process;
    private boolean closed;
    private final Thread shutdown = new Thread(this::close, "arena-host-shutdown");
    public LocalServer() { Runtime.getRuntime().addShutdownHook(shutdown); }
    public void start(int port) throws Exception {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("端口范围为1024至65535");
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false); probe.bind(new InetSocketAddress("0.0.0.0", port));
        } catch (IOException occupied) { throw new IOException("端口 " + port + " 已被占用，请换一个端口。", occupied); }
        Path code = Path.of(LocalServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path jar = code.getParent().resolve("server/arena-server.jar");
        if (!Files.isRegularFile(jar)) throw new IOException("缺少内置联机服务，请完整解压游戏目录。");
        Path directory = Path.of(System.getProperty("fightinggame.dataDir", "data")).toAbsolutePath();
        Files.createDirectories(directory);
        Path log = directory.resolve("host-" + port + ".log");
        synchronized (this) {
            if (closed) throw new IOException("房间已关闭");
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-Dfile.encoding=UTF-8", "-jar", jar.toString(), "--server.port=" + port,
                    "--server.address=0.0.0.0", "--spring.main.banner-mode=off")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long deadline = System.nanoTime() + 40_000_000_000L;
        while (System.nanoTime() < deadline && process.isAlive()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                        .timeout(Duration.ofSeconds(1)).GET().build();
                if (http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200 && process.isAlive()) return;
            } catch (IOException waiting) { /* The embedded server is still starting. */ }
            Thread.sleep(150);
        }
        close();
        throw new IOException("房间启动失败，详情见 " + log);
    }
    public boolean isAlive() { return process != null && process.isAlive(); }
    @Override public synchronized void close() {
        closed = true;
        if (process != null && process.isAlive()) process.destroyForcibly();
        if (Thread.currentThread() != shutdown) {
            try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException stopping) { }
        }
    }
}

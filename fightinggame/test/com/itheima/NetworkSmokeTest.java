package com.itheima;

import com.itheima.net.DuelClient;
import com.itheima.net.LocalServer;
import com.itheima.ui.OnlineDialog;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.net.ServerSocket;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

public final class NetworkSmokeTest {
    private static OnlineDialog dialog;
    private static DuelClient second;
    private static int checks;
    private static final AtomicReference<DuelClient> first = new AtomicReference<>();
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("arena-network-");
        System.setProperty("fightinggame.dataDir", temporary.toString());
        Path screenshots = Path.of(System.getProperty("arena.testOutput", "build/screenshots")).toAbsolutePath();
        Files.createDirectories(screenshots);
        int port;
        try (ServerSocket free = new ServerSocket(0)) { port = free.getLocalPort(); }
        LocalServer owned = null;
        try {
            check(DuelClient.endpoint("https://example.org:8443/").toString().equals("wss://example.org:8443/ws/duel"), "secure address conversion");
            try { DuelClient.endpoint("file:///tmp/demo"); throw new AssertionError("bad URL accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            try (ServerSocket occupied = new ServerSocket(port); LocalServer blocked = new LocalServer()) {
                try { blocked.start(port); throw new AssertionError("occupied port accepted"); }
                catch (java.io.IOException expected) { checks++; }
            }
            edt(() -> {
                try {
                    var theme = Class.forName("com.itheima.ui.GameTheme").getDeclaredMethod("install");
                    theme.setAccessible(true); theme.invoke(null);
                } catch (Exception failure) { throw new RuntimeException(failure); }
                dialog = new OnlineDialog(null, "玩家一");
                descendants(dialog, JSpinner.class).get(0).setValue(port);
                button(dialog, "创建房间").doClick();
            });
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
            await(() -> {
                first.set((DuelClient) field(dialog, "client"));
                return first.get() != null && first.get().available();
            }, "native host starts and connects", 55000);
            owned = (LocalServer) field(dialog, "server");
            edt(() -> {
                button(dialog, "入席").doClick();
                second = new DuelClient(DuelClient.endpoint("127.0.0.1:" + port), client -> {});
            });
            await(() -> first.get().seat() == 0 && first.get().available() && second.available(), "host claims first seat", 10000);
            edt(() -> second.command("join", "长昵称玩家二号竞技场挑战者"));
            await(() -> second.seat() == 1 && second.available(), "second player joins", 10000);
            edt(() -> { button(dialog, "准备").doClick(); second.command("ready", ""); });
            await(() -> first.get().state().path("phase").asText().equals("FIGHTING") && first.get().available() && second.available(), "both players start match", 10000);
            edt(() -> {
                check(button(dialog, "普通攻击").isEnabled(), "active native player can attack");
                button(dialog, "普通攻击").doClick();
            });
            await(() -> second.state().path("turn").asInt() == 2 && first.get().available(), "action broadcast to second player", 10000);
            edt(() -> {
                check(!button(dialog, "普通攻击").isEnabled(), "native controls gate opponent turn");
                check(second.state().path("fighters").get(1).path("hp").asInt() == 152, "server calculated damage");
            });
            screenshot(screenshots.resolve("desktop-online.png"));
            edt(() -> dialog.setSize(1000, 730));
            screenshot(screenshots.resolve("desktop-online-compact.png"));
            edt(() -> {
                for (JButton button : descendants(dialog, JButton.class)) if (button.isShowing() && button.getText() != null && !button.getText().isEmpty()) {
                    check(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + button.getInsets().left + button.getInsets().right <= button.getWidth(), "button fits: " + button.getText());
                }
                for (JLabel label : descendants(dialog, JLabel.class)) if (label.isShowing()) {
                    check(label.getPreferredSize().width <= label.getWidth(), "online label fits");
                }
                ((WebSocket) field(second, "socket")).sendClose(1001, "Reconnect test");
            });
            await(() -> !second.available(), "disconnect detected", 10000);
            await(() -> second.available() && second.seat() == 1 && second.state().path("phase").asText().equals("FIGHTING"), "automatic seat recovery", 12000);
            for (int i = 0; i < 30; i++) {
                AtomicReference<Boolean> done = new AtomicReference<>(false);
                AtomicReference<Integer> turn = new AtomicReference<>();
                edt(() -> {
                    done.set(first.get().state().path("phase").asText().equals("FINISHED"));
                    if (done.get()) return;
                    turn.set(first.get().state().path("turn").asInt());
                    (first.get().state().path("activeSeat").asInt() == 0 ? first.get() : second).command("action", "ATTACK");
                });
                if (done.get()) break;
                await(() -> first.get().available() && second.available() &&
                        (first.get().state().path("turn").asInt() > turn.get() || first.get().state().path("phase").asText().equals("FINISHED")), "fight progresses", 10000);
            }
            edt(() -> {
                check(first.get().state().path("phase").asText().equals("FINISHED"), "fight completes");
                check(first.get().state().path("winner").asInt() == 0, "winner matches turn order");
                button(dialog, "再战").doClick(); second.command("rematch", "");
            });
            await(() -> first.get().state().path("phase").asText().equals("FIGHTING") && first.get().state().path("activeSeat").asInt() == 1 && second.available(), "rematch alternates starter", 10000);
            edt(() -> second.command("resign", ""));
            await(() -> first.get().state().path("phase").asText().equals("FINISHED"), "resign settles match", 10000);
            edt(() -> dialog.dispose());
            LocalServer stopped = owned;
            await(() -> !stopped.isAlive(), "owned host stops on window close", 10000);
            System.out.println("PASS NETWORK: " + checks + " assertions; native host, two players, reconnect, rematch and shutdown");
        } finally {
            edt(() -> { if (second != null) second.close(); if (dialog != null) dialog.dispose(); for (Window window : Window.getWindows()) window.dispose(); });
            if (owned != null) owned.close();
            try (var files = Files.list(temporary)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
            Files.deleteIfExists(temporary);
        }
    }
    private static void screenshot(Path path) throws Exception {
        Thread.sleep(250);
        edt(() -> {
            BufferedImage image = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); dialog.paintAll(graphics); graphics.dispose();
            try { ImageIO.write(image, "png", path.toFile()); } catch (Exception failure) { throw new RuntimeException(failure); }
            java.util.Set<Integer> colors = new java.util.HashSet<>();
            for (int y = 220; y < image.getHeight() - 220; y += 4) for (int x = 40; x < image.getWidth() - 40; x += 4) colors.add(image.getRGB(x, y));
            check(colors.size() > 30, "native arena is nonblank");
        });
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }
    private static JButton button(Container parent, String text) {
        return descendants(parent, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }
    private static <T extends Component> List<T> descendants(Container parent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container container) found.addAll(descendants(container, type));
        }
        return found;
    }
    private static void await(BooleanSupplier condition, String message, long timeout) throws Exception {
        long deadline = System.nanoTime() + timeout * 1_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> result = new AtomicReference<>(); edt(() -> result.set(condition.getAsBoolean()));
            if (result.get()) { checks++; return; } Thread.sleep(50);
        }
        throw new AssertionError(message);
    }
    private static void edt(Runnable runnable) throws Exception { SwingUtilities.invokeAndWait(runnable); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}

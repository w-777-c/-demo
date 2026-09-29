package com.itheima;

import com.itheima.storage.UserStore;
import com.itheima.ui.GameFrame;
import com.itheima.ui.ArenaPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Swing 端到端冒烟测试：通过 Robot 操作窗口并检查主要交互、持久化和动画截图。 */
public final class GuiSmokeTest {
    private static GameFrame frame;
    private static Robot robot;
    private static int checks;

    public static void main(String[] args) throws Exception {
        // 使用临时数据目录，避免测试账号和设置污染开发者本地存档。
        Path dir = Files.createTempDirectory("arena-gui-");
        System.setProperty("fightinggame.dataDir", dir.toString());
        Path file = dir.resolve("accounts.properties");
        Files.createDirectories(Path.of("build", "screenshots"));
        robot = new Robot();
        try {
            UserStore store = new UserStore(file);
            edt(() -> {
                try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
                catch (Exception exception) { throw new RuntimeException(exception); }
                frame = new GameFrame(store, new Random(8)); frame.setVisible(true);
            });
            robot.waitForIdle();
            screenshot("home");
            SwingUtilities.invokeLater(() -> button(frame, "角色图鉴").doClick());
            JDialog archive = waitDialog("角色图鉴 · CONTRACT ARCHIVE");
            screenshot("archive", archive);
            edt(() -> {
                button(archive, "狂刃").doClick();
                check(!descendants(archive, javax.swing.JTextArea.class).get(0).getText().isBlank(), "archive story is populated");
                archive.dispose();
            });
            await(() -> !archive.isShowing(), "archive closes");
            SwingUtilities.invokeLater(() -> descendants(frame, JButton.class).stream().filter(b -> "音画设置".equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow().doClick());
            JDialog settings = waitDialog("音画设置");
            screenshot("settings", settings);
            edt(() -> {
                List<javax.swing.JSlider> sliders = descendants(settings, javax.swing.JSlider.class);
                sliders.get(0).setValue(18); sliders.get(1).setValue(35);
                button(settings, "保存设置").doClick();
            });
            await(() -> !settings.isShowing(), "settings saved");
            check(com.itheima.ui.UiSettings.load(dir.resolve("presentation.properties")).music() == 18, "music preference persisted");
            SwingUtilities.invokeLater(() -> descendants(frame, JButton.class).stream().filter(b -> "音画设置".equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow().doClick());
            JDialog cancelledSettings = waitDialog("音画设置");
            edt(() -> {
                descendants(cancelledSettings, javax.swing.JSlider.class).get(0).setValue(0);
                button(cancelledSettings, "取消").doClick();
                check(com.itheima.ui.UiSettings.current().music() == 18, "cancel restores volume");
            });
            edt(() -> check(!button(frame, "普通攻击").isEnabled(), "battle controls disabled before start"));
            SwingUtilities.invokeLater(() -> button(frame, "登录 / 注册").doClick());
            JDialog account = waitDialog("账号");
            edt(() -> {
                descendants(account, JToggleButton.class).get(1).doClick();
            });
            screenshot("account", account);
            edt(() -> {
                credentials(account, "Tester1", "abc123");
                button(account, "注册并登录").doClick();
            });
            await(() -> !account.isShowing(), "registration completed");
            check(new UserStore(file).find("Tester1") != null, "registration persisted");
            SwingUtilities.invokeLater(() -> button(frame, "开始挑战").doClick());
            JDialog creation = waitDialog("创建挑战者");
            screenshot("creation", creation);
            edt(() -> button(creation, "OK", "确定").doClick());
            await(() -> !creation.isShowing(), "character created");
            robot.waitForIdle();
            screenshot("battle");
            edt(() -> {
                check(button(frame, "普通攻击").isEnabled(), "attack enabled");
                check(!button(frame, "药水 (3)").isEnabled(), "potion disabled at full health");
                check(!button(frame, "退出登录").isEnabled(), "account fixed during game");
            });
            AtomicReference<Point> target = new AtomicReference<>();
            edt(() -> {
                JButton attack = button(frame, "普通攻击");
                Point position = attack.getLocationOnScreen(); position.translate(attack.getWidth() / 2, attack.getHeight() / 2); target.set(position);
            });
            robot.mouseMove(target.get().x, target.get().y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            BufferedImage firstFrame = captureArena();
            Thread.sleep(120);
            BufferedImage nextFrame = captureArena();
            int changed = 0;
            for (int y = 0; y < firstFrame.getHeight(); y++) {
                for (int x = 0; x < firstFrame.getWidth(); x++) {
                    if (firstFrame.getRGB(x, y) != nextFrame.getRGB(x, y)) changed++;
                }
            }
            check(changed > 200, "attack and health bar animate between frames");
            screenshot("impact");
            edt(() -> button(frame, "生命汲取").doClick());
            edt(() -> check(!button(frame, "汲取 (2)").isEnabled(), "drain cooldown reflected in UI"));
            edt(() -> {
                button(frame, "防御").doClick(); button(frame, "防御").doClick();
                check(button(frame, "灵魂潮汐").isEnabled(), "charged ultimate enabled");
                button(frame, "灵魂潮汐").doClick();
                check(!button(frame, "灵魂潮汐").isEnabled(), "ultimate consumes energy in UI");
            });
            for (int i = 0; i < 30; i++) {
                AtomicReference<Boolean> enabled = new AtomicReference<>();
                edt(() -> enabled.set(button(frame, "普通攻击").isEnabled()));
                if (!enabled.get()) break;
                edt(() -> button(frame, "普通攻击").doClick());
            }
            edt(() -> check(button(frame, "选择战后奖励").isEnabled(), "first battle victory"));
            screenshot("victory");
            SwingUtilities.invokeLater(() -> button(frame, "选择战后奖励").doClick());
            JDialog reward = waitDialog("战后奖励");
            screenshot("rewards", reward);
            edt(() -> descendants(reward, JButton.class).stream().filter(b -> !b.getText().isEmpty()).findFirst().orElseThrow().doClick());
            await(() -> !reward.isShowing(), "camp reward selected");
            edt(() -> { button(frame, "下一场战斗").doClick(); frame.setSize(1000, 760); });
            robot.waitForIdle();
            screenshot("compact");
            edt(() -> {
                for (JButton button : descendants(frame, JButton.class)) {
                    if (button.isShowing() && button.getText() != null && !button.getText().isEmpty()) {
                        int required = button.getFontMetrics(button.getFont()).stringWidth(button.getText())
                                + button.getInsets().left + button.getInsets().right;
                        check(required <= button.getWidth(), "button text fits: " + button.getText());
                        String detail = button.getAccessibleContext().getAccessibleDescription();
                        if (detail != null && !detail.equals(button.getToolTipText())) {
                            int detailWidth = button.getFontMetrics(button.getFont().deriveFont(11f)).stringWidth(detail) + 28;
                            check(detailWidth <= button.getWidth(), "skill detail fits: " + detail);
                        }
                    }
                }
                for (JLabel label : descendants(frame, JLabel.class)) {
                    if (label.isShowing() && label.getText() != null && !label.getText().isEmpty()) {
                        check(label.getPreferredSize().height <= label.getHeight(), "sidebar text fits vertically");
                        check(label.getPreferredSize().width <= label.getWidth(), "sidebar text fits horizontally");
                    }
                }
                for (javax.swing.JViewport viewport : descendants(frame, javax.swing.JViewport.class)) {
                    check(viewport.getView().getWidth() <= viewport.getWidth(), "scrolling panels do not clip horizontally");
                }
            });
            SwingUtilities.invokeLater(() -> button(frame, "撤退结算").doClick());
            JDialog retirement = waitDialog("撤退结算");
            edt(() -> button(retirement, "Yes", "是", "是(Y)").doClick());
            await(() -> !retirement.isShowing(), "retirement completed");
            check(new UserStore(file).find("Tester1").getGames() == 1, "one game saved");
            check(new UserStore(file).find("Tester1").getBestWins() == 1, "victory saved");
            edt(() -> button(frame, "退出登录").doClick());
            SwingUtilities.invokeLater(() -> button(frame, "登录 / 注册").doClick());
            JDialog relogin = waitDialog("账号");
            edt(() -> { credentials(relogin, "Tester1", "abc123"); button(relogin, "登录").doClick(); });
            await(() -> !relogin.isShowing(), "saved account login");
            SwingUtilities.invokeLater(() -> button(frame, "战绩榜").doClick());
            JDialog leaderboard = waitDialog("本地战绩榜");
            screenshot("leaderboard", leaderboard);
            edt(() -> button(leaderboard, "OK", "确定").doClick());
            System.out.println("PASS GUI: " + checks + " assertions; screenshots in build/screenshots");
        } finally {
            edt(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            Files.deleteIfExists(file); Files.deleteIfExists(dir.resolve("presentation.properties")); Files.deleteIfExists(dir);
        }
    }

    private static void credentials(Container parent, String username, String password) {
        for (JTextField field : descendants(parent, JTextField.class)) {
            field.setText(field instanceof JPasswordField ? password : username);
        }
    }

    private static JButton button(Container parent, String... labels) {
        for (JButton button : descendants(parent, JButton.class)) {
            for (String label : labels) if (label.equals(button.getText())) return button;
        }
        throw new AssertionError("Missing button " + String.join("/", labels));
    }

    private static <T extends Component> List<T> descendants(Container parent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) found.add(type.cast(component));
            if (component instanceof Container child) found.addAll(descendants(child, type));
        }
        return found;
    }

    private static JDialog waitDialog(String title) throws Exception {
        AtomicReference<JDialog> result = new AtomicReference<>();
        await(() -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof JDialog dialog && dialog.isShowing() && title.equals(dialog.getTitle())) { result.set(dialog); return true; }
            }
            return false;
        }, "dialog opens: " + title);
        return result.get();
    }

    private static void await(BooleanSupplier condition, String name) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> value = new AtomicReference<>(false);
            edt(() -> value.set(condition.getAsBoolean()));
            if (value.get()) { checks++; return; }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out: " + name);
    }

    private static void screenshot(String name) throws Exception {
        screenshot(name, frame);
    }

    private static void screenshot(String name, Window window) throws Exception {
        robot.waitForIdle();
        AtomicReference<Point> pointer = new AtomicReference<>();
        edt(() -> { Point point = window.getLocationOnScreen(); point.translate(window.getWidth() / 2, 34); pointer.set(point); });
        robot.mouseMove(pointer.get().x, pointer.get().y);
        Thread.sleep(200);
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        edt(() -> {
            BufferedImage rendered = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = rendered.createGraphics(); window.paintAll(graphics); graphics.dispose(); image.set(rendered);
        });
        ImageIO.write(image.get(), "png", Path.of("build", "screenshots", name + ".png").toFile());
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 100; y < image.get().getHeight() - 100; y += 5) {
            for (int x = 20; x < image.get().getWidth() - 20; x += 5) colors.add(image.get().getRGB(x, y));
        }
        check(colors.size() > (window == frame ? 30 : 5), "nonblank rendered window " + name);
    }

    private static BufferedImage captureArena() throws Exception {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        edt(() -> {
            ArenaPanel arena = descendants(frame, ArenaPanel.class).get(0);
            BufferedImage rendered = new BufferedImage(arena.getWidth(), arena.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = rendered.createGraphics(); arena.paintAll(graphics); graphics.dispose(); image.set(rendered);
        });
        return image.get();
    }

    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); checks++; }
}

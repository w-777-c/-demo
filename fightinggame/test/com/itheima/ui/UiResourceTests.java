package com.itheima.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/** UI 资源回归测试：检查图标、立绘、音频文件和不同动作帧确实可用。 */
public final class UiResourceTests {
    private static int checks;
    public static void main(String[] args) throws Exception {
        // 资源测试不依赖窗口显示，适合在无图形桌面的构建环境中运行。
        Path temp = Files.createTempDirectory("arena-presentation-");
        System.setProperty("fightinggame.dataDir", temp.toString());
        try {
            Font body = UiAssets.body(Font.PLAIN, 16), title = UiAssets.display(32);
            check(!body.getFamily().equals("Dialog") && !title.getFamily().equals("Dialog"), "bundled fonts loaded");
            check(body.canDisplayUpTo("铁境竞技场生命攻击防御灵魂潮汐胜利账号音画设置012ABC") < 0, "body Chinese glyphs present");
            check(title.canDisplayUpTo("绯幕剧场铁境竞技场战斗胜利音画设置") < 0, "display Chinese glyphs present");
            for (String icon : new String[]{"sword", "swords", "shield", "heart-pulse", "flask-conical", "sparkles", "trophy", "users", "settings-2", "x", "crown", "gem", "chevron-right", "music-2", "volume-2"}) check(UiAssets.image(icon).getWidth() == 64, "icon " + icon);
            BufferedImage sheet = new BufferedImage(1440, 840, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics(); GameArt.scenery(g, 1440, 840, 0);
            for (int style = 0; style < 6; style++) {
                BufferedImage actor = GameArt.actorImage(style); int opaque = 0;
                for (int y = 0; y < actor.getHeight(); y++) for (int x = 0; x < actor.getWidth(); x++) if ((actor.getRGB(x, y) >>> 24) != 0) opaque++;
                check(opaque > 60000, "nonblank complete actor " + style);
                GameArt.actor(g, 120 + style * 240, 780, 600, style, false, true, 0);
                BufferedImage idle = pose(style, 0, 1);
                for (int action = 1; action <= 6; action++) {
                    BufferedImage motion = pose(style, action, .45);
                    check(differentPixels(idle, motion) > 700, "distinct pose " + style + "/" + action);
                }
            }
            g.dispose(); Files.createDirectories(Path.of("build/screenshots")); ImageIO.write(sheet, "png", Path.of("build/screenshots/cast.png").toFile());
            for (String name : new String[]{"overture", "battle", "hover", "click", "attack", "guard", "heal", "magic", "victory", "defeat", "open"}) {
                try (var raw = UiResourceTests.class.getResourceAsStream("/audio/" + name + ".wav")) {
                    check(raw != null, "audio resource " + name);
                    try (var pcm = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                        double seconds = pcm.getFrameLength() / pcm.getFormat().getFrameRate();
                        check(seconds > (name.equals("overture") || name.equals("battle") ? 25 : 0.02), "audio duration " + name);
                        byte[] bytes = pcm.readAllBytes(); long energy = 0; int peak = 0;
                        for (int i = 0; i + 1 < bytes.length; i += 2) { int sample = (short) ((bytes[i] & 255) | bytes[i + 1] << 8); energy += (long) sample * sample; peak = Math.max(peak, Math.abs(sample)); }
                        check(peak < 32767 && energy / (bytes.length / 2) > 100, "audible unclipped audio " + name);
                    }
                }
            }
            Path preferences = temp.resolve("settings.properties");
            UiSettings first = UiSettings.load(preferences); first.setMusic(-8); first.setEffects(135); first.setMotion(false); first.save();
            UiSettings second = UiSettings.load(preferences); check(second.music() == 0 && second.effects() == 100 && !second.motion(), "clamped settings round trip");
            Files.writeString(preferences, "music=invalid\neffects=20\n");
            check(UiSettings.load(preferences).music() == 32, "invalid preferences fall back");
            verifyAudioDevice();
            System.out.println("PASS presentation: " + checks + " assertions; cast in build/screenshots/cast.png");
        } finally { try (var entries = Files.list(temp)) { for (Path entry : entries.toList()) Files.delete(entry); } Files.delete(temp); }
    }
    private static void verifyAudioDevice() throws Exception {
        Clip clip;
        try { clip = AudioSystem.getClip(); }
        catch (IllegalArgumentException | javax.sound.sampled.LineUnavailableException absent) { System.out.println("SKIP audio device: " + absent.getMessage()); return; }
        try (clip; var input = AudioSystem.getAudioInputStream(UiResourceTests.class.getResource("/audio/click.wav"))) {
            try { clip.open(input); } catch (javax.sound.sampled.LineUnavailableException absent) { System.out.println("SKIP audio device: " + absent.getMessage()); return; }
            clip.start(); Thread.sleep(100); check(clip.getLongFramePosition() > 0, "audio device advances playback");
        }
    }
    private static BufferedImage pose(int style, int action, double phase) {
        BufferedImage image = new BufferedImage(260, 360, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); GameArt.quality(graphics);
        GameArt.actor(graphics, 130, 350, 330, style, false, true, 0, action, phase); graphics.dispose(); return image;
    }
    private static int differentPixels(BufferedImage first, BufferedImage second) {
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y += 2) for (int x = 0; x < first.getWidth(); x += 2) if (first.getRGB(x, y) != second.getRGB(x, y)) changed++;
        return changed;
    }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); checks++; }
}

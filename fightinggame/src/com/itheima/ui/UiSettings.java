package com.itheima.ui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** 管理可持久化的演示设置：音乐、音效音量和动态效果开关。 */
public final class UiSettings {
    private static final UiSettings CURRENT = load(Path.of(System.getProperty("fightinggame.dataDir", "data"), "presentation.properties"));
    private final Path file;
    private volatile int music = 32, effects = 55;
    private volatile boolean motion = true;
    private UiSettings(Path file) { this.file = file; }
    public static UiSettings current() { return CURRENT; }
    public int music() { return music; }
    public int effects() { return effects; }
    public boolean motion() { return motion; }
    public void setMusic(int value) { music = Math.max(0, Math.min(100, value)); }
    public void setEffects(int value) { effects = Math.max(0, Math.min(100, value)); }
    public void setMotion(boolean value) { motion = value; }
    public static UiSettings load(Path file) {
        UiSettings settings = new UiSettings(file);
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
                settings.setMusic(Integer.parseInt(properties.getProperty("music", "32")));
                settings.setEffects(Integer.parseInt(properties.getProperty("effects", "55")));
                settings.setMotion(Boolean.parseBoolean(properties.getProperty("motion", "true")));
            } catch (IOException | IllegalArgumentException invalid) { System.err.println("Presentation settings: " + invalid.getMessage()); }
        }
        return settings;
    }
    /** 使用同目录临时文件和原子替换保存设置，避免写入中断留下半个文件。 */
    public synchronized void save() throws IOException {
        Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "presentation-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("music", Integer.toString(music)); properties.setProperty("effects", Integer.toString(effects));
            properties.setProperty("motion", Boolean.toString(motion));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { properties.store(writer, "Iron Arena presentation settings"); }
            try { Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}

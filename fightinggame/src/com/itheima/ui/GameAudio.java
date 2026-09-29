package com.itheima.ui;

import java.io.BufferedInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/** 音频设备操作全部移出 Swing 线程；没有可用声卡时只禁用声音，不阻塞游戏。 */
public final class GameAudio {
    private static GameAudio instance;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "arena-audio"); thread.setDaemon(true); return thread;
    });
    private final Map<String, Clip> clips = new HashMap<>();
    private volatile String status = "音乐准备中";
    private String scene = "overture", playing = "";
    private boolean paused, unavailable;
    private long lastHover;
    private GameAudio() {}
    private static synchronized GameAudio get() { if (instance == null) instance = new GameAudio(); return instance; }
    public static void start() { GameAudio audio = get(); audio.submit(audio::updateMusic); }
    public static synchronized String status() { return instance == null ? "音频未启动" : instance.status; }
    private void submit(Runnable task) {
        try { worker.execute(task); }
        catch (java.util.concurrent.RejectedExecutionException closed) { /* A closing window can still emit its final focus event. */ }
    }
    public static void scene(boolean battle) {
        GameAudio audio = get();
        audio.submit(() -> { audio.scene = battle ? "battle" : "overture"; audio.updateMusic(); });
    }
    public static void volumeChanged() {
        GameAudio audio = get(); audio.submit(audio::updateMusic);
    }
    public static void pause(boolean value) {
        GameAudio audio = get(); audio.submit(() -> { audio.paused = value; audio.updateMusic(); });
    }
    public static synchronized void effect(String sound) {
        GameAudio audio = instance;
        if (audio == null) return;
        if (UiSettings.current().effects() == 0) return;
        audio.submit(() -> {
            if (sound.equals("hover")) {
                long now = System.nanoTime();
                if (now - audio.lastHover < 90_000_000L) return;
                audio.lastHover = now;
            }
            Clip clip = audio.clip(sound);
            if (clip == null) return;
            clip.stop(); clip.setFramePosition(0); gain(clip, UiSettings.current().effects()); clip.start();
        });
    }
    private Clip clip(String name) {
        if (unavailable) return null;
        if (clips.containsKey(name)) return clips.get(name);
        try (var input = GameAudio.class.getResourceAsStream("/audio/" + name + ".wav")) {
            if (input == null) throw new java.io.IOException("Missing audio " + name);
            try (var pcm = AudioSystem.getAudioInputStream(new BufferedInputStream(input))) {
                Clip clip = AudioSystem.getClip();
                try { clip.open(pcm); } catch (Exception failure) { clip.close(); throw failure; }
                clips.put(name, clip); status = "音频已就绪"; return clip;
            }
        } catch (Exception failure) {
            unavailable = true; status = "音频设备不可用";
            System.err.println("Audio disabled: " + failure.getMessage()); return null;
        }
    }
    private void updateMusic() {
        int volume = UiSettings.current().music();
        if (!playing.isEmpty() && (!playing.equals(scene) || paused || volume == 0)) {
            clips.get(playing).stop(); playing = "";
        }
        if (paused || volume == 0) return;
        Clip clip = clip(scene);
        if (clip == null) return;
        gain(clip, volume);
        if (!playing.equals(scene)) { clip.setFramePosition(0); clip.loop(Clip.LOOP_CONTINUOUSLY); playing = scene; }
    }
    private static void gain(Clip clip, int volume) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), (float) (20 * Math.log10(Math.max(1, volume) / 100.0)))));
        }
    }
    public static synchronized void shutdown() {
        if (instance == null) return;
        GameAudio audio = instance; instance = null;
        audio.worker.execute(() -> { for (Clip clip : audio.clips.values()) { clip.stop(); clip.close(); } audio.clips.clear(); });
        audio.worker.shutdown();
    }
}

package com.itheima.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/** 音乐、音效和动态效果设置，取消时恢复打开窗口前的值。 */
@SuppressWarnings("serial")
final class SettingsDialog extends GameDialog {
    private final UiSettings settings = UiSettings.current();
    private final int previousMusic = settings.music(), previousEffects = settings.effects();
    private final boolean previousMotion = settings.motion();
    private boolean saved, disposed;
    SettingsDialog(Window owner) {
        super(owner, "音画设置");
        JPanel content = new JPanel(new BorderLayout(0, 22));
        content.setBackground(GameTheme.BACKGROUND); content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 16)); rows.setOpaque(false);
        rows.add(volume("背景音乐", "music-2", previousMusic, true));
        rows.add(volume("交互音效", "volume-2", previousEffects, false));
        JCheckBox motion = new JCheckBox("战斗动态效果", previousMotion); motion.setOpaque(false);
        motion.addActionListener(event -> settings.setMotion(motion.isSelected())); rows.add(motion);
        content.add(rows, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 14)); footer.setOpaque(false);
        JLabel feedback = new JLabel(GameAudio.status()); feedback.setForeground(GameTheme.MUTED); footer.add(feedback, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); buttons.setOpaque(false);
        GameButton cancel = new GameButton("取消", GameTheme.SURFACE); cancel.addActionListener(event -> dispose()); buttons.add(cancel);
        GameButton accept = new GameButton("保存设置", GameTheme.GREEN);
        accept.addActionListener(event -> {
            try { settings.save(); saved = true; dispose(); }
            catch (java.io.IOException failure) { feedback.setText("设置保存失败，请检查数据目录权限。"); feedback.setForeground(GameTheme.RED); }
        }); buttons.add(accept); footer.add(buttons, BorderLayout.SOUTH); content.add(footer, BorderLayout.SOUTH);
        setContentPane(content); getRootPane().setDefaultButton(accept); pack(); setResizable(false); setLocationRelativeTo(owner);
    }
    private JPanel volume(String name, String glyph, int initial, boolean music) {
        JPanel row = new JPanel(new BorderLayout(12, 0)); row.setOpaque(false);
        JLabel label = new JLabel(name, UiAssets.icon(glyph, 20), JLabel.LEFT); label.setForeground(GameTheme.TEXT); row.add(label, BorderLayout.WEST);
        JSlider slider = new JSlider(0, 100, initial); slider.setOpaque(false); slider.setPreferredSize(new Dimension(210, 36));
        slider.getAccessibleContext().setAccessibleName(name);
        JLabel value = new JLabel(initial + "%", JLabel.RIGHT); value.setForeground(GameTheme.GOLD); value.setPreferredSize(new Dimension(44, 30));
        slider.addChangeListener(event -> {
            value.setText(slider.getValue() + "%");
            if (music) { settings.setMusic(slider.getValue()); GameAudio.volumeChanged(); }
            else { settings.setEffects(slider.getValue()); if (!slider.getValueIsAdjusting()) GameAudio.effect("click"); }
        }); row.add(slider, BorderLayout.CENTER); row.add(value, BorderLayout.EAST); return row;
    }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        if (!saved) { settings.setMusic(previousMusic); settings.setEffects(previousEffects); settings.setMotion(previousMotion); GameAudio.volumeChanged(); }
        super.dispose();
    }
}

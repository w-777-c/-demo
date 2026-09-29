package com.itheima.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import javax.swing.JPanel;

/** 复用的角色卡绘制组件，支持侧栏头像、创建角色预览和图鉴全身卡。 */
@SuppressWarnings("serial")
final class CharacterPreview extends JPanel {
    private int style = 2;
    private final boolean portrait;
    private final boolean archive;
    CharacterPreview(boolean portrait) {
        this(portrait, false);
    }
    CharacterPreview(boolean portrait, boolean archive) {
        this.portrait = portrait; this.archive = archive; setOpaque(false);
        setPreferredSize(archive ? new Dimension(360, 548) : new Dimension(portrait ? 180 : 195, portrait ? 132 : 290));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, archive ? 548 : portrait ? 132 : 290)); setAlignmentX(0);
        getAccessibleContext().setAccessibleName("契约者立绘");
    }
    void setStyle(int value) { style = Math.max(0, Math.min(5, value)); repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); GameArt.quality(g);
        if (archive) {
            paintArchive(g);
        } else {
            g.setColor(new Color(57, 30, 42)); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(196, 157, 98, 55));
            for (int x = -getHeight(); x < getWidth(); x += 24) g.drawLine(x, 0, x + getHeight(), getHeight());
            if (portrait) {
                g.setColor(new Color(229, 204, 157, 30)); g.fillOval(getWidth() / 2 - 58, 20, 116, 116);
                g.drawImage(GameArt.actorImage(style), (getWidth() - 228) / 2, -8, 228, 342, null);
            } else GameArt.actor(g, getWidth() / 2.0, getHeight() - 3, getHeight() - 12, style, false, true, 0);
            GameArt.frame(g, 0, 0, getWidth(), getHeight());
        }
        g.dispose();
    }
    private void paintArchive(Graphics2D g) {
        Color[] accents = {new Color(189, 112, 91), new Color(160, 71, 91), new Color(93, 160, 196), new Color(128, 166, 177), new Color(184, 118, 103), new Color(121, 112, 190)};
        Color accent = accents[style % accents.length];
        g.setPaint(new GradientPaint(0, 0, new Color(231, 242, 246), getWidth(), getHeight(), new Color(183, 211, 225)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(255, 255, 255, 115));
        for (int i = 0; i < 28; i++) {
            int x = 18 + (i * 71) % Math.max(1, getWidth() - 36), y = 18 + (i * 97) % Math.max(1, getHeight() - 36);
            int size = i % 5 == 0 ? 4 : 2; GameArt.diamond(g, x, y, size);
        }
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 42));
        g.fillOval(getWidth() / 2 - 170, 34, 340, 340);
        g.setColor(accent); g.setStroke(new java.awt.BasicStroke(2));
        g.drawOval(getWidth() / 2 - 151, 53, 302, 302); g.drawOval(getWidth() / 2 - 132, 72, 264, 264);
        g.setColor(new Color(255, 255, 255, 150));
        for (int i = 0; i < 8; i++) { double angle = i * Math.PI / 4; GameArt.diamond(g, getWidth() / 2 + Math.cos(angle) * 153, 204 + Math.sin(angle) * 153, 4); }
        GameArt.actor(g, getWidth() / 2.0, getHeight() - 22, Math.min(540, getHeight() - 28), style, false, true, 0);
        g.setColor(new Color(36, 51, 68, 190)); g.fillRect(18, getHeight() - 72, getWidth() - 36, 45);
        g.setColor(Color.WHITE); g.setFont(GameTheme.display(25));
        String title = switch (style) { case 0 -> "铁卫"; case 1 -> "狂刃"; case 2 -> "灵术师"; case 3 -> "银幕守卫"; case 4 -> "绯刃使"; default -> "星辉术士"; };
        g.drawString(title, 34, getHeight() - 38);
        g.setFont(GameTheme.font(Font.PLAIN, 10)); g.setColor(new Color(229, 240, 245)); g.drawString("CRIMSON THEATRE  /  CONTRACT ARCHIVE", 34, getHeight() - 20);
        GameArt.frame(g, 0, 0, getWidth(), getHeight());
    }
}

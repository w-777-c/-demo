package com.itheima.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

@SuppressWarnings("serial")
final class CharacterPreview extends JPanel {
    private int style = 2;
    private final boolean portrait;
    CharacterPreview(boolean portrait) {
        this.portrait = portrait; setOpaque(false);
        setPreferredSize(new Dimension(portrait ? 180 : 195, portrait ? 132 : 290));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, portrait ? 132 : 290)); setAlignmentX(0);
        getAccessibleContext().setAccessibleName("契约者立绘");
    }
    void setStyle(int value) { style = value; repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); GameArt.quality(g);
        g.setColor(new Color(57, 30, 42)); g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(196, 157, 98, 55));
        for (int x = -getHeight(); x < getWidth(); x += 24) g.drawLine(x, 0, x + getHeight(), getHeight());
        if (portrait) {
            g.drawImage(GameArt.actorImage(style), (getWidth() - 228) / 2, -8, 228, 342, null);
        } else GameArt.actor(g, getWidth() / 2.0, getHeight() - 3, getHeight() - 12, style, false, true, 0);
        GameArt.frame(g, 0, 0, getWidth(), getHeight()); g.dispose();
    }
}

package com.itheima.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;

@SuppressWarnings("serial")
final class GameButton extends JButton {
    private String detail;
    private Color accent;

    GameButton(String text, Color color) {
        super(text);
        setFont(GameTheme.font(Font.BOLD, 13));
        setBackground(color);
        setForeground(color.equals(GameTheme.GREEN) ? GameTheme.BACKGROUND : GameTheme.TEXT);
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        setRolloverEnabled(true);
    }

    void setDetail(String detail, Color accent) {
        this.detail = detail;
        this.accent = accent;
        getAccessibleContext().setAccessibleDescription(detail);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = isEnabled() ? getBackground() : GameTheme.SURFACE;
        if (isEnabled() && getModel().isRollover()) base = base.brighter();
        if (isEnabled() && getModel().isPressed()) base = base.darker();
        g.setColor(base);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g.setColor(isFocusOwner() ? GameTheme.GREEN : isEnabled() && getModel().isRollover() ? GameTheme.MUTED : GameTheme.BORDER);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        if (detail == null) {
            g.dispose();
            super.paintComponent(graphics);
            return;
        }
        g.setColor(isEnabled() ? accent : GameTheme.BORDER);
        g.fillRoundRect(14, 13, 20, 3, 3, 3);
        g.setFont(getFont());
        g.setColor(isEnabled() ? GameTheme.TEXT : GameTheme.MUTED);
        g.drawString(getText(), 14, 38);
        g.setFont(GameTheme.font(Font.PLAIN, 11));
        g.setColor(GameTheme.MUTED);
        g.drawString(detail, 14, 59);
        g.dispose();
    }
}

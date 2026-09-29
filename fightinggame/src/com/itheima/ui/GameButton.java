package com.itheima.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Timer;

/** 票券式按钮：统一绘制边框、图标、详情、副标题、焦点和悬停反馈。 */
@SuppressWarnings("serial")
final class GameButton extends JButton {
    private String detail, glyph;
    private Color accent = GameTheme.GOLD;
    private float hover;
    private final Timer transition;
    GameButton(String text, Color color) {
        super(text); setFont(GameTheme.font(Font.PLAIN, 14)); setBackground(color); setForeground(GameTheme.TEXT);
        setOpaque(false); setContentAreaFilled(false); setFocusPainted(false); setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)); setRolloverEnabled(true);
        transition = new Timer(16, event -> {
            float target = isEnabled() && getModel().isRollover() ? 1 : 0;
            hover += (target - hover) * 0.32f;
            if (Math.abs(target - hover) < 0.02f) { hover = target; ((Timer) event.getSource()).stop(); }
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { if (isEnabled()) { GameAudio.effect("hover"); animateHover(true); } }
            @Override public void mouseExited(MouseEvent event) { animateHover(false); }
        });
        addActionListener(event -> GameAudio.effect("click"));
    }
    private void animateHover(boolean entered) {
        if (UiSettings.current().motion()) transition.start();
        else { transition.stop(); hover = entered ? 1 : 0; repaint(); }
    }
    @Override public void removeNotify() { transition.stop(); super.removeNotify(); }
    void setGlyph(String name) { glyph = name; repaint(); }
    void setDetail(String detail, Color accent) { this.detail = detail; this.accent = accent; getAccessibleContext().setAccessibleDescription(detail); repaint(); }
    @Override public Dimension getPreferredSize() {
        if (getText().isEmpty() && glyph != null) return new Dimension(40, 38);
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width + (glyph == null || detail != null ? 0 : 24), detail == null ? Math.max(38, size.height) : 90);
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight(), notch = 7;
        Path2D shape = new Path2D.Double();
        shape.moveTo(notch, 1); shape.lineTo(w - 1, 1); shape.lineTo(w - 1, h - notch); shape.lineTo(w - notch, h - 1); shape.lineTo(1, h - 1); shape.lineTo(1, notch); shape.closePath();
        boolean prominent = getBackground().equals(GameTheme.GREEN) || getBackground().equals(GameTheme.CRIMSON);
        g.setColor(prominent && isEnabled() ? new Color(111, 39, 52) : GameTheme.SURFACE); g.fill(shape);
        if (hover > 0 && isEnabled()) { g.setColor(new Color(228, 189, 115, (int) (hover * 25))); g.fill(shape); }
        g.setColor(isEnabled() && (isFocusOwner() || hover > 0.2 || prominent) ? GameTheme.GOLD : GameTheme.BORDER); g.draw(shape);
        if (prominent) { g.setColor(new Color(228, 189, 115, 100)); g.drawLine(10, 5, w - 6, 5); }
        if (getModel().isPressed()) g.translate(1, 1);
        if (detail != null) {
            g.setColor(isEnabled() ? accent : GameTheme.BORDER); g.drawLine(13, 12, w - 13, 12);
            if (glyph != null) g.drawImage(UiAssets.image(glyph), 13, 20, 21, 21, null);
            g.setColor(isEnabled() ? GameTheme.TEXT : GameTheme.MUTED); g.setFont(getFont()); g.drawString(getText(), 13, 57);
            g.setFont(GameTheme.font(Font.PLAIN, 11)); g.setColor(GameTheme.MUTED); g.drawString(detail, 13, 76);
            g.setColor(isEnabled() ? accent : GameTheme.BORDER); GameArt.diamond(g, w - 17, 30, 4);
        } else {
            g.setFont(getFont()); int textWidth = g.getFontMetrics().stringWidth(getText());
            int total = textWidth + (glyph == null || getText().isEmpty() ? 0 : 26); int x = (w - total) / 2;
            if (glyph != null) { g.drawImage(UiAssets.image(glyph), getText().isEmpty() ? (w - 20) / 2 : x, (h - 20) / 2, 20, 20, null); x += 26; }
            g.setColor(isEnabled() ? GameTheme.TEXT : GameTheme.MUTED);
            g.drawString(getText(), x, (h - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
        }
        g.dispose();
    }
}

package com.itheima.ui;

import com.itheima.game.GameSession;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

@SuppressWarnings("serial")
final class ChallengeTrack extends JComponent {
    private GameSession session;

    ChallengeTrack() {
        setPreferredSize(new Dimension(600, 38));
        getAccessibleContext().setAccessibleName("挑战进度");
    }

    @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {};
        return accessibleContext;
    }

    void setSession(GameSession session) {
        this.session = session;
        getAccessibleContext().setAccessibleDescription(session == null ? "尚未开始" : "已完成 " + session.getWins() + " 场战斗");
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int wins = session == null ? 0 : session.getWins();
        int first = session != null && !session.isChallenge() ? wins / 10 * 10 : 0;
        int gap = 6;
        int cell = (getWidth() - gap * 9) / 10;
        g.setFont(GameTheme.font(Font.PLAIN, 11));
        for (int i = 0; i < 10; i++) {
            int stage = first + i;
            boolean completed = wins > stage;
            boolean current = session != null && wins == stage && session.getState() != GameSession.State.FINISHED;
            int x = i * (cell + gap);
            g.setColor(completed ? new java.awt.Color(39, 70, 59) : GameTheme.SURFACE);
            g.fillRoundRect(x, 0, cell, 28, 4, 4);
            g.setColor(completed ? GameTheme.GREEN : current ? GameTheme.GOLD : GameTheme.MUTED);
            if (current) g.drawRoundRect(x, 0, cell - 1, 27, 4, 4);
            String text = String.format("%02d", stage + 1);
            g.drawString(text, x + (cell - g.getFontMetrics().stringWidth(text)) / 2, 18);
        }
        g.dispose();
    }
}

package com.itheima.ui;

import com.itheima.game.GameSession;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** 绘制十关挑战或无尽模式的菱形进度轨道。 */
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
        int cell = getWidth() / 10;
        g.setColor(GameTheme.BORDER); g.drawLine(cell / 2, 18, getWidth() - cell / 2, 18);
        g.setFont(GameTheme.font(Font.PLAIN, 11));
        for (int i = 0; i < 10; i++) {
            int stage = first + i;
            boolean completed = wins > stage;
            boolean current = session != null && wins == stage && session.getState() != GameSession.State.FINISHED;
            int x = i * cell + cell / 2;
            boolean elite = (stage + 1) % 3 == 0 || i == 9;
            g.setColor(current ? GameTheme.CRIMSON : GameTheme.SURFACE); GameArt.diamond(g, x, 18, 18);
            g.setColor(completed ? GameTheme.GREEN : current ? GameTheme.GOLD : GameTheme.MUTED);
            int r = elite ? 18 : 15;
            g.drawPolygon(new int[]{x, x + 13, x, x - 13}, new int[]{18 - r, 18, 18 + r, 18}, 4);
            String text = String.format("%02d", stage + 1);
            g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, 22);
        }
        g.dispose();
    }
}

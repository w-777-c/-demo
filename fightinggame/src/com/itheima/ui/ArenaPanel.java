package com.itheima.ui;

import com.itheima.doman.Character;
import com.itheima.game.GameSession;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.Timer;

@SuppressWarnings("serial")
public final class ArenaPanel extends JPanel {
    private GameSession session;
    private int tick;
    private final Timer animation;

    public ArenaPanel() {
        setPreferredSize(new Dimension(800, 320));
        setMinimumSize(new Dimension(600, 260));
        setBackground(GameFrame.BACKGROUND);
        animation = new Timer(60, event -> { tick++; repaint(); });
    }

    @Override public void addNotify() { super.addNotify(); animation.start(); }
    @Override public void removeNotify() { animation.stop(); super.removeNotify(); }
    public void setSession(GameSession session) { this.session = session; repaint(); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int ground = height - 36;
        g.setColor(new Color(28, 32, 36));
        g.fillRect(0, ground, width, 36);
        g.setColor(new Color(44, 49, 53));
        for (int x = 0; x < width; x += 64) {
            g.drawLine(x, ground, x - 40, height);
        }
        g.drawLine(0, ground, width, ground);
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? 50 : width - 90;
            g.setColor(new Color(33, 37, 42));
            g.fillRect(x, 72, 40, Math.max(60, ground - 72));
            g.fillRect(x - 12, 65, 64, 12);
            g.setColor(new Color(54, 58, 63));
            g.fillRect(x + 8, 76, 4, Math.max(30, ground - 82));
            g.setColor(new Color(196, 111, 66));
            g.fillRect(x + 14, 110, 12, 17);
            g.setColor(new Color(239, 190, 83));
            g.fillRect(x + 17, 106 + tick % 3, 6, 15);
        }
        Character hero = session == null ? null : session.getHero();
        Character enemy = session == null ? null : session.getEnemy();
        health(g, 24, 18, Math.min(280, width / 3), hero, "挑战者", GameFrame.GREEN);
        health(g, width - Math.min(280, width / 3) - 24, 18, Math.min(280, width / 3), enemy, "竞技场守卫", GameFrame.RED);
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 19));
        g.setColor(GameFrame.MUTED);
        center(g, "VS", width / 2, 60);
        int scale = Math.max(3, Math.min(5, (height - 95) / 45));
        int bob = tick % 24 < 12 ? 0 : 2;
        fighter(g, width / 4, ground - 4 - bob, scale, false, hero == null || hero.isAlive(), GameFrame.GREEN);
        Color enemyColor = session != null && session.getEnemy().getName().equals("神秘法师")
                ? new Color(169, 125, 197) : GameFrame.RED;
        fighter(g, width * 3 / 4, ground - 4 - bob, scale, true, enemy == null || enemy.isAlive(), enemyColor);
        if (session == null || session.getState() != GameSession.State.FIGHTING) {
            String title = session == null ? "铁境竞技场" : session.isCleared() ? "挑战通关"
                    : session.getState() == GameSession.State.RESTING ? "战斗胜利" : session.getHero().isAlive() ? "挑战结束" : "战斗失败";
            g.setColor(GameFrame.TEXT);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 27));
            center(g, title, width / 2, height / 2 - 10);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            g.setColor(GameFrame.MUTED);
            center(g, session == null ? "IRON ARENA" : "本局 " + session.getWins() + " 胜", width / 2, height / 2 + 18);
        } else {
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            g.setColor(GameFrame.MUTED);
            center(g, "ROUND " + session.getBattle().getRound(), width / 2, height / 2);
        }
        g.dispose();
    }

    private void health(Graphics2D g, int x, int y, int width, Character character, String fallback, Color color) {
        String name = character == null ? fallback : character.getName();
        int hp = character == null ? 100 : character.getHP();
        int max = character == null ? 100 : character.getMaxHP();
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        g.setColor(GameFrame.TEXT);
        g.drawString(name, x, y + 14);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        String amount = character == null ? "-- / --" : hp + " / " + max;
        g.drawString(amount, x + width - g.getFontMetrics().stringWidth(amount), y + 14);
        g.setColor(new Color(52, 57, 62));
        g.fillRect(x, y + 26, width, 8);
        g.setColor(color);
        g.fillRect(x, y + 26, (int) ((long) width * hp / max), 8);
        if (character != null && character.isDefending()) {
            g.drawString("防御中", x, y + 53);
        }
    }

    // Original pixel-art fighters rendered at integer scale; no external assets required.
    private void fighter(Graphics2D canvas, int x, int ground, int scale, boolean flip, boolean alive, Color armor) {
        Graphics2D g = (Graphics2D) canvas.create();
        g.translate(x, ground);
        if (!alive) { g.rotate(flip ? -Math.PI / 2 : Math.PI / 2); g.scale(0.75, 0.75); }
        g.scale(flip ? -scale : scale, scale);
        g.setColor(new Color(10, 12, 15));
        g.fillOval(-17, -2, 35, 4);
        g.setColor(new Color(38, 43, 49));
        g.fillRect(-8, -15, 7, 14); g.fillRect(3, -15, 7, 14);
        g.setColor(new Color(99, 108, 116));
        g.fillRect(-10, -3, 9, 3); g.fillRect(3, -3, 10, 3);
        g.setColor(armor.darker());
        g.fillRect(-12, -31, 7, 18); g.fillRect(-8, -29, 18, 15);
        g.setColor(armor);
        g.fillRect(-7, -29, 15, 12); g.fillRect(-12, -29, 7, 6); g.fillRect(9, -28, 6, 7);
        g.setColor(armor.brighter());
        g.fillRect(-6, -28, 3, 10); g.fillRect(0, -26, 6, 2);
        g.setColor(new Color(219, 174, 138));
        g.fillRect(-4, -39, 11, 10); g.fillRect(12, -22, 5, 5);
        g.setColor(new Color(175, 185, 191));
        g.fillRect(-6, -42, 14, 5); g.fillRect(-7, -39, 4, 10); g.fillRect(7, -39, 3, 7);
        g.setColor(armor);
        g.fillRect(-3, -45, 5, 4);
        g.setColor(new Color(29, 31, 34));
        g.fillRect(3, -36, 4, 2); g.fillRect(-8, -16, 18, 3);
        g.setColor(new Color(231, 189, 89));
        g.fillRect(-1, -16, 4, 3); g.fillRect(17, -23, 8, 2);
        g.setColor(new Color(207, 219, 221));
        g.fillRect(20, -41, 3, 18); g.fillRect(21, -44, 2, 3);
        g.setColor(new Color(110, 121, 129));
        g.fillRect(20, -20, 3, 5);
        g.setStroke(new BasicStroke(1));
        g.dispose();
    }

    private void center(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
    }
}

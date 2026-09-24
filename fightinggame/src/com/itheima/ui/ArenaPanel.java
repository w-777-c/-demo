package com.itheima.ui;

import com.itheima.doman.Character;
import com.itheima.game.Battle;
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
    private long actionStarted;
    private Battle.Action lastAction;
    private int heroChange;
    private int enemyChange;
    private double heroHealth = 1;
    private double enemyHealth = 1;

    public ArenaPanel() {
        setPreferredSize(new Dimension(800, 320));
        setMinimumSize(new Dimension(600, 240));
        setBackground(GameTheme.BACKGROUND);
        animation = new Timer(30, event -> {
            tick++;
            if (session != null) {
                heroHealth += (ratio(session.getHero()) - heroHealth) * 0.16;
                enemyHealth += (ratio(session.getEnemy()) - enemyHealth) * 0.16;
            }
            repaint();
        });
        getAccessibleContext().setAccessibleName("竞技场与双方生命状态");
    }

    @Override public void addNotify() { super.addNotify(); animation.start(); }
    @Override public void removeNotify() { animation.stop(); super.removeNotify(); }

    public void setSession(GameSession session) {
        this.session = session;
        heroHealth = session == null ? 1 : ratio(session.getHero());
        enemyHealth = session == null ? 1 : ratio(session.getEnemy());
        actionStarted = 0;
        repaint();
    }

    public void animateTurn(Battle.Action action, int previousHeroHP, int previousEnemyHP) {
        lastAction = action;
        heroChange = session.getHero().getHP() - previousHeroHP;
        enemyChange = session.getEnemy().getHP() - previousEnemyHP;
        actionStarted = System.nanoTime();
        repaint();
    }

    private double ratio(Character character) { return (double) character.getHP() / character.getMaxHP(); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int ground = height - 27;
        scenery(g, width, ground, height);
        Character hero = session == null ? null : session.getHero();
        Character enemy = session == null ? null : session.getEnemy();
        int barWidth = Math.min(285, width / 3);
        health(g, 20, 12, barWidth, hero, "挑战者", GameTheme.GREEN, heroHealth);
        health(g, width - barWidth - 20, 12, barWidth, enemy, "竞技场守卫", GameTheme.RED, enemyHealth);
        g.setFont(GameTheme.font(Font.BOLD, 12));
        g.setColor(GameTheme.MUTED);
        center(g, "VS", width / 2, 36);

        int scale = Math.max(2, Math.min(5, (height - 94) / 45));
        int bob = tick % 48 < 24 ? 0 : 2;
        double elapsed = actionStarted == 0 ? 1 : (System.nanoTime() - actionStarted) / 700_000_000.0;
        double progress = Math.min(1, elapsed);
        boolean attacks = lastAction == Battle.Action.ATTACK || lastAction == Battle.Action.POWER_STRIKE || lastAction == Battle.Action.DRAIN;
        int lunge = progress < 0.42 && attacks ? (int) (Math.sin(progress / 0.42 * Math.PI) * 22) : 0;
        int retaliation = progress > 0.42 && progress < 0.82 && heroChange < 0 && enemy != null && enemy.isAlive()
                ? (int) (Math.sin((progress - 0.42) / 0.4 * Math.PI) * 16) : 0;
        int heroX = width / 4 + lunge;
        int enemyX = width * 3 / 4 - retaliation;
        int style = enemyStyle();
        Color enemyColor = style == 4 ? new Color(159, 160, 206) : style == 3 ? new Color(156, 168, 171) : GameTheme.RED;
        fighter(g, heroX, ground - 7 - bob, scale, false, hero == null || hero.isAlive(), GameTheme.GREEN, 0);
        fighter(g, enemyX, ground - 7 - bob, scale, true, enemy == null || enemy.isAlive(), enemyColor, style);
        if (hero != null && hero.isDefending()) guard(g, heroX, ground - scale * 22, scale, GameTheme.GREEN);
        if (enemy != null && enemy.isDefending()) guard(g, enemyX, ground - scale * 22, scale, GameTheme.GOLD);

        if (session == null || session.getState() != GameSession.State.FIGHTING) {
            String title = session == null ? "铁境竞技场" : session.isCleared() ? "挑战通关"
                    : session.getState() == GameSession.State.RESTING ? "战斗胜利" : session.getHero().isAlive() ? "挑战结束" : "战斗失败";
            g.setColor(session == null ? GameTheme.TEXT : session.getHero().isAlive() ? GameTheme.GOLD : GameTheme.RED);
            g.setFont(GameTheme.font(Font.BOLD, 23));
            center(g, title, width / 2, height / 2 - 3);
            g.setFont(GameTheme.font(Font.PLAIN, 11));
            g.setColor(GameTheme.MUTED);
            center(g, session == null ? "IRON ARENA" : "本局 " + session.getWins() + " 胜", width / 2, height / 2 + 22);
        } else {
            g.setFont(GameTheme.font(Font.PLAIN, 10));
            g.setColor(GameTheme.MUTED);
            center(g, "ROUND", width / 2, height / 2 - 10);
            g.setFont(GameTheme.font(Font.BOLD, 28));
            g.setColor(GameTheme.GOLD);
            center(g, String.format("%02d", session.getBattle().getRound()), width / 2, height / 2 + 23);
        }
        if (progress < 1) {
            floatingHealth(g, heroChange, width / 4, ground - scale * 47, progress);
            floatingHealth(g, enemyChange, width * 3 / 4, ground - scale * 47, progress);
        }
        g.dispose();
    }

    private void scenery(Graphics2D g, int width, int ground, int height) {
        g.setColor(new Color(23, 29, 31));
        g.fillRect(0, 66, width, ground - 66);
        g.setColor(new Color(32, 39, 41));
        for (int y = 88; y < ground; y += 36) {
            g.drawLine(0, y, width, y);
            for (int x = (y / 36 % 2) * 45; x < width; x += 90) g.drawLine(x, y, x, Math.min(y + 36, ground));
        }
        int gateWidth = Math.min(176, width / 4);
        g.setColor(new Color(41, 49, 51));
        g.fillRoundRect((width - gateWidth) / 2 - 7, 77, gateWidth + 14, ground - 64, 80, 80);
        g.setColor(new Color(17, 22, 23));
        g.fillRoundRect((width - gateWidth) / 2, 84, gateWidth, ground - 70, 72, 72);
        g.setColor(new Color(30, 37, 39));
        for (int x = width / 2 - gateWidth / 2 + 16; x < width / 2 + gateWidth / 2; x += 24) {
            g.fillRect(x, 101, 3, ground - 101);
        }
        g.setColor(new Color(35, 43, 45));
        g.fillRect(0, ground, width, height - ground);
        g.setColor(new Color(63, 74, 75));
        g.drawLine(0, ground, width, ground);
        for (int x = 0; x < width; x += 72) g.drawLine(x, ground, x - 24, height);
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? 42 : width - 64;
            g.setColor(new Color(43, 50, 52));
            g.fillRect(x, 82, 22, ground - 82);
            g.fillRect(x - 7, 78, 36, 8);
            g.setColor(new Color(60, 68, 69));
            g.fillRect(x + 3, 88, 3, ground - 90);
            g.setColor(GameTheme.GOLD);
            g.fillRect(x + 8, 107, 7, 17);
            g.setColor(new Color(240, 152, 80));
            g.fillRect(x + 6, 115, 11, 10);
            g.setColor(new Color(242, 223, 156));
            g.fillRect(x + 10, 105 + tick % 4, 3, 16);
        }
    }

    private void health(Graphics2D g, int x, int y, int width, Character character, String fallback, Color color, double displayed) {
        String name = character == null ? fallback : character.getName();
        String amount = character == null ? "-- / --" : character.getHP() + " / " + character.getMaxHP();
        g.setFont(GameTheme.font(Font.PLAIN, 10));
        int amountWidth = g.getFontMetrics().stringWidth(amount);
        g.setFont(GameTheme.font(Font.BOLD, 13));
        int nameWidth = width - amountWidth - 12;
        if (g.getFontMetrics().stringWidth(name) > nameWidth) {
            while (!name.isEmpty() && g.getFontMetrics().stringWidth(name + "...") > nameWidth) name = name.substring(0, name.length() - 1);
            name += "...";
        }
        g.setColor(GameTheme.TEXT);
        g.drawString(name, x, y + 12);
        g.setFont(GameTheme.font(Font.PLAIN, 10));
        g.setColor(GameTheme.MUTED);
        g.drawString(amount, x + width - g.getFontMetrics().stringWidth(amount), y + 12);
        g.setColor(GameTheme.RAISED);
        g.fillRoundRect(x, y + 24, width, 8, 4, 4);
        g.setColor(color);
        g.fillRoundRect(x, y + 24, Math.max(0, (int) (width * displayed)), 8, 4, 4);
        if (character != null) {
            g.setFont(GameTheme.font(Font.PLAIN, 10));
            g.setColor(character.isDefending() ? GameTheme.GOLD : GameTheme.MUTED);
            g.drawString(character.isDefending() ? "防御姿态" : "ATK " + character.getAttack() + "   /   DEF " + character.getDefense(), x, y + 48);
        }
    }

    private int enemyStyle() {
        if (session == null) return 1;
        if (session.getEnemy().getName().equals("守关者")) return 5;
        return switch (session.getEnemy().getSkill()) {
            case HEAVY_STRIKE -> 1;
            case DOUBLE_STRIKE -> 2;
            case GUARD -> 3;
            case FIREBALL -> 4;
        };
    }

    private void guard(Graphics2D g, int x, int y, int scale, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2));
        int edge = 18 * scale;
        g.drawLine(x - edge, y - 20, x - edge, y + 20);
        g.drawLine(x - edge, y - 20, x - edge + 8, y - 20);
        g.drawLine(x + edge, y - 20, x + edge, y + 20);
        g.drawLine(x + edge - 8, y + 20, x + edge, y + 20);
    }

    private void floatingHealth(Graphics2D g, int amount, int x, int y, double progress) {
        if (amount == 0) return;
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) (1 - progress)));
        g.setFont(GameTheme.font(Font.BOLD, 17));
        g.setColor(amount > 0 ? GameTheme.GREEN : GameTheme.RED);
        center(g, "HP " + (amount > 0 ? "+" : "") + amount, x, Math.max(90, y) - (int) (progress * 16));
        g.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    // Pixel-art silhouettes distinguish enemy classes without external runtime assets.
    private void fighter(Graphics2D canvas, int x, int ground, int scale, boolean flip, boolean alive, Color armor, int style) {
        Graphics2D g = (Graphics2D) canvas.create();
        g.translate(x, ground);
        g.setColor(new Color(10, 15, 15));
        g.fillOval(-16 * scale, -scale, 33 * scale, 3 * scale);
        g.scale(flip ? -scale : scale, scale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (!alive) {
            g.setColor(new Color(77, 90, 91)); g.fillRect(-22, -5, 14, 5);
            g.setColor(armor.darker()); g.fillRect(-12, -8, 17, 8);
            g.setColor(armor); g.fillRect(-9, -8, 13, 3);
            g.setColor(new Color(175, 188, 188)); g.fillRect(5, -7, 9, 7);
            g.setColor(new Color(219, 180, 148)); g.fillRect(7, -5, 7, 3);
            g.setColor(GameTheme.GOLD); g.fillRect(17, -5, 2, 5);
            g.setColor(new Color(175, 188, 188)); g.fillRect(19, -3, 10, 2);
            g.dispose();
            return;
        }
        g.setColor(armor.darker().darker());
        g.fillRect(-12, -30, 9, style == 4 || style == 5 ? 27 : 19);
        g.setColor(new Color(44, 52, 54));
        g.fillRect(-8, -15, 7, 14); g.fillRect(3, -15, 7, 14);
        g.setColor(new Color(132, 145, 145));
        g.fillRect(-10, -3, 9, 3); g.fillRect(3, -3, 10, 3);
        g.setColor(armor.darker());
        g.fillRect(-8, -29, 18, 15);
        g.setColor(armor);
        g.fillRect(-7, -29, 15, 12); g.fillRect(-12, -29, 7, 6); g.fillRect(9, -28, 6, 7);
        g.setColor(armor.brighter());
        g.fillRect(-6, -28, 3, 10); g.fillRect(0, -26, 6, 2);
        g.setColor(new Color(219, 180, 148));
        g.fillRect(-4, -39, 11, 10); g.fillRect(12, -22, 5, 5);
        g.setColor(style == 4 || style == 2 ? armor.darker() : new Color(175, 188, 188));
        g.fillRect(-6, -42, 14, 5); g.fillRect(-7, -39, 4, 10); g.fillRect(7, -39, 3, 7);
        g.setColor(style == 5 ? GameTheme.GOLD : armor);
        g.fillRect(-3, -45, 5, 4);
        if (style == 5) { g.fillRect(-7, -46, 3, 7); g.fillRect(5, -46, 3, 7); }
        g.setColor(new Color(28, 34, 35));
        g.fillRect(3, -36, 4, 2); g.fillRect(-8, -16, 18, 3);
        if (style == 2) g.fillRect(-3, -33, 10, 4);
        g.setColor(GameTheme.GOLD);
        g.fillRect(-1, -16, 4, 3);
        if (style == 3) {
            g.setColor(new Color(97, 111, 115)); g.fillRect(12, -32, 14, 21);
            g.setColor(new Color(183, 193, 190)); g.fillRect(13, -31, 12, 2); g.fillRect(13, -29, 2, 17);
            g.setColor(GameTheme.GOLD); g.fillRect(18, -28, 3, 14); g.fillRect(15, -24, 9, 3);
        } else if (style == 4) {
            g.setColor(new Color(139, 153, 144)); g.fillRect(20, -42, 2, 37);
            g.setColor(GameTheme.GOLD); g.fillRect(17, -42, 8, 2);
            g.setColor(armor.brighter()); g.fillRect(19, -47, 4, 4); g.fillRect(20, -49, 2, 8);
        } else {
            g.setColor(GameTheme.GOLD); g.fillRect(17, -23, 8, 2);
            g.setColor(new Color(217, 228, 222));
            g.fillRect(20, style == 2 ? -33 : -41, style == 5 ? 5 : 3, style == 2 ? 10 : 18);
            g.fillRect(21, style == 2 ? -35 : -44, 2, 3);
            g.setColor(new Color(116, 132, 135)); g.fillRect(20, -20, 3, 5);
            if (style == 2) { g.setColor(new Color(217, 228, 222)); g.fillRect(-14, -24, 2, 10); }
        }
        g.dispose();
    }

    private void center(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y);
    }
}

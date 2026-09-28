package com.itheima.ui;

import com.itheima.doman.Character;
import com.itheima.game.Battle;
import com.itheima.game.GameSession;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import javax.swing.JPanel;
import javax.swing.Timer;

@SuppressWarnings("serial")
public final class ArenaPanel extends JPanel {
    private GameSession session;
    private boolean online;
    private Character onlineHero, onlineEnemy;
    private String onlineTitle = "等待对手", onlineSubtitle = "";
    private int tick, heroChange, enemyChange;
    private final Timer animation;
    private long actionStarted;
    private Battle.Action lastAction;
    private double heroHealth = 1, enemyHealth = 1;

    public ArenaPanel() {
        setPreferredSize(new Dimension(800, 370));
        setMinimumSize(new Dimension(600, 260));
        setBackground(GameTheme.BACKGROUND);
        animation = new Timer(30, event -> {
            boolean motion = UiSettings.current().motion();
            if (motion) tick++;
            heroHealth += ((hero() == null ? 1 : ratio(hero())) - heroHealth) * (motion ? 0.16 : 1);
            enemyHealth += ((enemy() == null ? 1 : ratio(enemy())) - enemyHealth) * (motion ? 0.16 : 1);
            repaint();
        });
        getAccessibleContext().setAccessibleName("竞技场与双方生命状态");
    }
    @Override public void addNotify() { super.addNotify(); animation.start(); }
    @Override public void removeNotify() { animation.stop(); super.removeNotify(); }
    public void setSession(GameSession value) {
        online = false; session = value;
        heroHealth = hero() == null ? 1 : ratio(hero()); enemyHealth = enemy() == null ? 1 : ratio(enemy());
        actionStarted = 0; repaint();
    }
    private Character hero() { return online ? onlineHero : session == null ? null : session.getHero(); }
    private Character enemy() { return online ? onlineEnemy : session == null ? null : session.getEnemy(); }
    public void setDuel(Character first, Character second, String title, String subtitle) {
        heroChange = first == null || onlineHero == null ? 0 : first.getHP() - onlineHero.getHP();
        enemyChange = second == null || onlineEnemy == null ? 0 : second.getHP() - onlineEnemy.getHP();
        online = true; onlineHero = first; onlineEnemy = second; onlineTitle = title; onlineSubtitle = subtitle;
        if (heroChange != 0 || enemyChange != 0) {
            lastAction = heroChange > 0 || enemyChange > 0 ? Battle.Action.POTION : Battle.Action.ATTACK;
            actionStarted = System.nanoTime(); GameAudio.effect(lastAction == Battle.Action.POTION ? "heal" : "attack");
        }
        repaint();
    }
    public void animateTurn(Battle.Action action, int previousHeroHP, int previousEnemyHP) {
        if (session == null) return;
        lastAction = action; heroChange = session.getHero().getHP() - previousHeroHP;
        enemyChange = session.getEnemy().getHP() - previousEnemyHP; actionStarted = System.nanoTime(); repaint();
    }
    private double ratio(Character character) { return (double) character.getHP() / character.getMaxHP(); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create(); GameArt.quality(g);
        int w = getWidth(), h = getHeight(), ground = h - 30;
        GameArt.scenery(g, w, h, session == null || online ? 0 : Math.min(3, session.getWins() / 3));
        Character hero = hero(), enemy = enemy();
        int barWidth = Math.min(285, (w - 110) / 2);
        health(g, 20, 25, barWidth, hero, "契约者", GameTheme.GREEN, heroHealth);
        health(g, w - barWidth - 20, 25, barWidth, enemy, "剧场守卫", GameTheme.RED, enemyHealth);
        g.setFont(GameTheme.display(24)); g.setColor(GameTheme.GOLD); center(g, "VS", w / 2, 45);
        double progress = actionStarted == 0 || !UiSettings.current().motion() ? 1 : Math.min(1, (System.nanoTime() - actionStarted) / 800_000_000.0);
        boolean attacks = lastAction == Battle.Action.ATTACK || lastAction == Battle.Action.POWER_STRIKE || lastAction == Battle.Action.DRAIN;
        int lunge = progress < 0.45 && attacks ? (int) (Math.sin(progress / 0.45 * Math.PI) * 28) : 0;
        int retaliation = progress > 0.45 && progress < 0.85 && heroChange < 0 ? (int) (Math.sin((progress - 0.45) / 0.4 * Math.PI) * 18) : 0;
        int heroX = w / 4 + lunge, enemyX = w * 3 / 4 - retaliation;
        double actorHeight = Math.max(110, Math.min(h - 108, w * 0.60));
        double breathe = UiSettings.current().motion() ? Math.sin(tick * 0.055) : 0;
        GameArt.actor(g, heroX, ground, actorHeight, session == null || online ? 2 : session.getHero().getStyle().ordinal(), false, hero == null || hero.isAlive(), breathe);
        int enemyStyle = session == null || online ? 4 : switch (session.getEnemy().getSkill()) { case GUARD, HEAVY_STRIKE -> 3; case DOUBLE_STRIKE -> 4; case FIREBALL -> 5; };
        GameArt.actor(g, enemyX, ground, actorHeight, enemyStyle, true, enemy == null || enemy.isAlive(), -breathe);
        if (hero != null && hero.isDefending()) guard(g, heroX, ground - actorHeight * 0.40, actorHeight);
        if (enemy != null && enemy.isDefending()) guard(g, enemyX, ground - actorHeight * 0.40, actorHeight);
        if (progress < 1) {
            effects(g, heroX, enemyX, ground, actorHeight, progress);
            floating(g, heroChange, w / 4, 94, progress); floating(g, enemyChange, w * 3 / 4, 94, progress);
        }
        String title, subtitle;
        if (online) { title = onlineTitle; subtitle = onlineSubtitle; }
        else if (session == null) { title = "绯幕序章"; subtitle = "THE CRIMSON THEATRE"; }
        else if (session.getState() == GameSession.State.FIGHTING) { title = String.format("第 %02d 回合", session.getBattle().getRound()); subtitle = "命运的交锋"; }
        else {
            title = session.isCleared() ? "终幕荣光" : session.getState() == GameSession.State.RESTING ? "战斗胜利" : session.getHero().isAlive() ? "远征落幕" : "战斗失败";
            subtitle = "本局 " + session.getWins() + " 胜";
        }
        int textY = Math.max(125, h / 2);
        g.setFont(GameTheme.display(28)); g.setColor(GameTheme.GOLD); center(g, title, w / 2, textY);
        g.setFont(GameTheme.font(Font.PLAIN, 11)); g.setColor(GameTheme.TEXT); center(g, subtitle, w / 2, textY + 25);
        g.setColor(GameTheme.GOLD); GameArt.diamond(g, w / 2.0, textY + 44, 4);
        GameArt.frame(g, 0, 0, w, h); g.dispose();
    }
    private void health(Graphics2D g, int x, int y, int width, Character character, String fallback, Color color, double shown) {
        g.setColor(new Color(20, 14, 20, 220)); g.fillRect(x - 5, y - 5, width + 10, 57);
        String amount = character == null ? "-- / --" : character.getHP() + " / " + character.getMaxHP();
        g.setFont(GameTheme.font(Font.PLAIN, 12)); int amountWidth = g.getFontMetrics().stringWidth(amount);
        g.setColor(GameTheme.TEXT); g.drawString(amount, x + width - amountWidth, y + 13);
        String name = character == null ? fallback : character.getName();
        while (name.length() > 1 && g.getFontMetrics().stringWidth(name) > width - amountWidth - 18) name = name.substring(0, name.length() - 1);
        g.drawString(name, x, y + 13);
        g.setColor(GameTheme.BORDER); g.fillRect(x, y + 24, width, 8);
        g.setColor(color); g.fillRect(x, y + 24, (int) (width * Math.max(0, Math.min(1, shown))), 8);
        g.setColor(GameTheme.GOLD); g.drawLine(x, y + 37, x + width, y + 37);
    }
    private void guard(Graphics2D g, double x, double y, double height) {
        g.setColor(new Color(115, 199, 221, 30)); int radius = (int) (height * 0.21);
        g.fillOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
        g.setColor(GameTheme.ICE); g.setStroke(new BasicStroke(2)); g.drawOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
    }
    private void effects(Graphics2D canvas, int heroX, int enemyX, int ground, double height, double progress) {
        Graphics2D g = (Graphics2D) canvas.create();
        g.setComposite(AlphaComposite.SrcOver.derive((float) (1 - progress)));
        double y = ground - height * 0.48;
        if (lastAction == Battle.Action.DEFEND) guard(g, heroX, y, height * 1.3);
        else if (lastAction == Battle.Action.POTION || lastAction == Battle.Action.DRAIN) {
            g.setColor(GameTheme.GREEN); g.setStroke(new BasicStroke(3));
            for (int i = 0; i < 4; i++) { int x = heroX - 36 + i * 24, cy = (int) (y + 55 - progress * 90 + i % 2 * 20); g.drawLine(x - 5, cy, x + 5, cy); g.drawLine(x, cy - 5, x, cy + 5); }
            if (lastAction == Battle.Action.DRAIN) { g.setColor(GameTheme.ICE); g.draw(new Arc2D.Double(heroX, y - 40, enemyX - heroX, 80, 0, 180, Arc2D.OPEN)); }
        } else {
            int x = progress < 0.45 ? enemyX : heroX;
            if (progress < 0.45 || heroChange < 0) {
                g.setColor(lastAction == Battle.Action.POWER_STRIKE ? GameTheme.GOLD : GameTheme.ICE); g.setStroke(new BasicStroke(lastAction == Battle.Action.POWER_STRIKE ? 6 : 3));
                g.drawLine(x - 42, (int) y + 42, x + 38, (int) y - 42);
                g.setStroke(new BasicStroke(1)); g.drawLine(x - 50, (int) y + 35, x + 28, (int) y - 50);
            }
        }
        g.dispose();
    }
    private void floating(Graphics2D g, int change, int x, int y, double progress) {
        if (change == 0) return;
        g.setColor(change > 0 ? GameTheme.GREEN : GameTheme.RED); g.setFont(GameTheme.font(Font.BOLD, 21));
        center(g, (change > 0 ? "+" : "") + change, x, y - (int) (progress * 16));
    }
    private void center(Graphics2D g, String text, int x, int y) { g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y); }
}

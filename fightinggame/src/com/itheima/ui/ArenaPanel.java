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
import java.awt.geom.Ellipse2D;
import java.awt.geom.QuadCurve2D;
import javax.swing.JPanel;
import javax.swing.Timer;

/** The combat stage. One clock drives poses, impact effects, damage numbers and health interpolation. */
@SuppressWarnings("serial")
public final class ArenaPanel extends JPanel {
    private static final long ACTION_NANOS = 1_100_000_000L;
    private GameSession session;
    private boolean online;
    private Character onlineHero, onlineEnemy;
    private String onlineTitle = "等待对手", onlineSubtitle = "";
    private int tick, heroChange, enemyChange, actionToken;
    private final Timer animation;
    private long actionStarted;
    private Battle.Action lastAction, responseAction = Battle.Action.ATTACK;
    private boolean ultimate;
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
        clearAction(); repaint();
    }
    private Character hero() { return online ? onlineHero : session == null ? null : session.getHero(); }
    private Character enemy() { return online ? onlineEnemy : session == null ? null : session.getEnemy(); }

    public void setDuel(Character first, Character second, String title, String subtitle) {
        heroChange = first == null || onlineHero == null ? 0 : first.getHP() - onlineHero.getHP();
        enemyChange = second == null || onlineEnemy == null ? 0 : second.getHP() - onlineEnemy.getHP();
        online = true; onlineHero = first; onlineEnemy = second; onlineTitle = title; onlineSubtitle = subtitle;
        if (heroChange != 0 || enemyChange != 0) startAction(enemyChange > 0 ? Battle.Action.POTION : Battle.Action.ATTACK, false, Battle.Action.ATTACK);
        repaint();
    }
    public void animateTurn(Battle.Action action, int previousHeroHP, int previousEnemyHP) {
        if (session == null) return;
        heroChange = session.getHero().getHP() - previousHeroHP;
        enemyChange = session.getEnemy().getHP() - previousEnemyHP;
        startAction(action, false, session.getBattle().getLastEnemyAction());
    }
    public void animateUltimate(int previousHeroHP, int previousEnemyHP) {
        if (session == null) return;
        heroChange = session.getHero().getHP() - previousHeroHP;
        enemyChange = session.getEnemy().getHP() - previousEnemyHP;
        startAction(Battle.Action.POWER_STRIKE, true, session.getBattle().getLastEnemyAction());
    }
    private void startAction(Battle.Action action, boolean ultimateAction, Battle.Action response) {
        lastAction = action; ultimate = ultimateAction; responseAction = response == null ? Battle.Action.ATTACK : response;
        actionStarted = System.nanoTime(); actionToken++;
        repaint();
    }
    private void clearAction() { actionStarted = 0; lastAction = null; ultimate = false; heroChange = enemyChange = 0; }
    private double ratio(Character character) { return (double) character.getHP() / character.getMaxHP(); }

    private double progress() {
        if (actionStarted == 0 || !UiSettings.current().motion()) return 1;
        return Math.max(0, Math.min(1, (System.nanoTime() - actionStarted) / (double) ACTION_NANOS));
    }
    private static double ease(double value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
    }
    private static double window(double value, double from, double to) {
        return ease((value - from) / (to - from));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create(); GameArt.quality(g);
        int w = getWidth(), h = getHeight(), ground = h - 30;
        GameArt.scenery(g, w, h, session == null || online ? 0 : Math.min(3, session.getWins() / 3));
        ambient(g, w, h);
        Character hero = hero(), enemy = enemy();
        int barWidth = Math.min(285, (w - 110) / 2);
        health(g, 20, 25, barWidth, hero, "契约者", GameTheme.GREEN, heroHealth);
        health(g, w - barWidth - 20, 25, barWidth, enemy, "剧场守卫", GameTheme.RED, enemyHealth);
        g.setFont(GameTheme.display(24)); g.setColor(GameTheme.GOLD); center(g, "VS", w / 2, 45);

        double p = progress();
        boolean playerMove = lastAction != null && p < 0.87;
        double playerPhase = playerMove ? window(p, 0.02, 0.76) : 1;
        double responsePhase = p < 0.47 ? 0 : window(p, 0.47, 0.98);
        int shake = UiSettings.current().motion() && p > 0.30 && p < 0.55 ? (int) (Math.sin(p * 130) * (ultimate ? 5 : 2)) : 0;
        int lunge = playerMove ? playerLunge(lastAction, playerPhase, w) : 0;
        int retaliation = heroChange < 0 && responsePhase > 0 ? playerLunge(responseAction, responsePhase, w) : 0;
        int heroX = w / 4 + lunge + shake, enemyX = w * 3 / 4 - retaliation + shake;
        double actorHeight = Math.max(110, Math.min(h - 108, w * 0.60));
        double breathe = UiSettings.current().motion() ? Math.sin(tick * 0.055) : 0;
        int heroStyle = session == null || online ? 2 : session.getHero().getStyle().ordinal();
        int enemyStyle = enemyStyle();
        GameArt.actor(g, heroX, ground, actorHeight, heroStyle, false, hero == null || hero.isAlive(), breathe,
                playerMove ? actionCode(lastAction, ultimate) : 0, playerPhase);
        GameArt.actor(g, enemyX, ground, actorHeight, enemyStyle, true, enemy == null || enemy.isAlive(), -breathe,
                heroChange < 0 && responsePhase > 0 ? actionCode(responseAction, false) : 0, responsePhase);
        if (hero != null && hero.isDefending()) shield(g, heroX, ground - actorHeight * 0.42, actorHeight, p, GameTheme.ICE);
        if (enemy != null && enemy.isDefending()) shield(g, enemyX, ground - actorHeight * 0.42, actorHeight, p, GameTheme.GOLD);
        actionEffects(g, p, responsePhase, heroX, enemyX, ground, actorHeight, heroStyle, enemyStyle);
        if (p > .78 && (hero != null && !hero.isAlive() || enemy != null && !enemy.isAlive())) defeatBurst(g, enemy != null && !enemy.isAlive() ? enemyX : heroX, ground - actorHeight * .42, actorHeight, p);
        if (p > .78 && session != null && session.getState() != GameSession.State.FIGHTING) celebration(g, w, h, p);
        if (p < 1) {
            floating(g, heroChange, w / 4, 94, p); floating(g, enemyChange, w * 3 / 4, 94, p);
        }

        String title, subtitle;
        if (online) { title = onlineTitle; subtitle = onlineSubtitle; }
        else if (session == null) { title = "绯幕序章"; subtitle = "THE CRIMSON THEATRE"; }
        else if (session.getState() == GameSession.State.FIGHTING) { title = String.format("第 %02d 回合", session.getBattle().getRound()); subtitle = "命运的交锋"; }
        else { title = session.isCleared() ? "终幕荣光" : session.getState() == GameSession.State.RESTING ? "战斗胜利" : session.getHero().isAlive() ? "远征落幕" : "战斗失败"; subtitle = "本局 " + session.getWins() + " 胜"; }
        int textY = Math.max(125, h / 2);
        g.setFont(GameTheme.display(28)); g.setColor(GameTheme.GOLD); center(g, title, w / 2, textY);
        g.setFont(GameTheme.font(Font.PLAIN, 11)); g.setColor(GameTheme.TEXT); center(g, subtitle, w / 2, textY + 25);
        g.setColor(GameTheme.GOLD); GameArt.diamond(g, w / 2.0, textY + 44, 4);
        GameArt.frame(g, 0, 0, w, h); g.dispose();
    }

    private int enemyStyle() {
        if (session == null || online || session.getEnemy() == null) return 4;
        return switch (session.getEnemy().getSkill()) { case GUARD, HEAVY_STRIKE -> 3; case DOUBLE_STRIKE -> 4; case FIREBALL -> 5; };
    }
    private int actionCode(Battle.Action action, boolean ultimateAction) {
        if (ultimateAction) return 6;
        return switch (action) { case ATTACK -> 1; case POWER_STRIKE -> 2; case DRAIN -> 3; case DEFEND -> 4; case POTION -> 5; };
    }
    private int playerLunge(Battle.Action action, double phase, int width) {
        if (action == null || action == Battle.Action.DEFEND || action == Battle.Action.POTION) return 0;
        double strike = Math.sin(Math.PI * Math.max(0, Math.min(1, phase)));
        int max = action == Battle.Action.POWER_STRIKE || ultimate ? Math.min(58, width / 15) : Math.min(34, width / 25);
        if (action == Battle.Action.DRAIN) max = Math.min(24, width / 30);
        return (int) (strike * max);
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

    private void ambient(Graphics2D g, int width, int height) {
        if (!UiSettings.current().motion()) return;
        Color[] colors = {GameTheme.GOLD, GameTheme.ICE, GameTheme.GREEN};
        for (int i = 0; i < 12; i++) {
            double phase = (tick * .018 + i * .17) % 1;
            int x = 42 + (i * 83) % Math.max(1, width - 84);
            int y = 94 + (int) (phase * Math.max(1, height - 170));
            int alpha = (int) (45 + 35 * Math.sin(phase * Math.PI));
            g.setColor(new Color(colors[i % colors.length].getRed(), colors[i % colors.length].getGreen(), colors[i % colors.length].getBlue(), alpha));
            GameArt.diamond(g, x, y, i % 3 == 0 ? 3 : 2);
        }
    }

    private void actionEffects(Graphics2D canvas, double p, double response, int heroX, int enemyX, int ground, double height, int heroStyle, int enemyStyle) {
        if (lastAction == null || !UiSettings.current().motion()) return;
        Graphics2D g = (Graphics2D) canvas.create();
        double y = ground - height * .50;
        drawAction(g, lastAction, heroX, enemyX, y, p, heroStyle, true, ultimate);
        if (response > 0 && (heroChange < 0 || responseAction == Battle.Action.DEFEND)) drawAction(g, responseAction, enemyX, heroX, y, response, enemyStyle, false, false);
        g.dispose();
    }
    private void drawAction(Graphics2D g, Battle.Action action, int source, int target, double y, double p, int style, boolean heroSide, boolean ultimateAction) {
        int role = style % 3;
        Color accent = role == 0 ? GameTheme.GREEN : role == 1 ? GameTheme.RED : GameTheme.ICE;
        if (action == Battle.Action.DEFEND) { shield(g, source, y, 230, p, heroSide ? GameTheme.ICE : GameTheme.GOLD); return; }
        if (action == Battle.Action.POTION) { potion(g, source, y, p); return; }
        if (action == Battle.Action.DRAIN) {
            // A hero's life drain pulls energy from the target; an enemy fireball travels toward the hero.
            drain(g, heroSide ? target : source, heroSide ? source : target, y, p, accent); return;
        }
        double hit = window(p, .22, .70);
        if (hit <= 0 || hit >= 1) return;
        int impactX = target + (int) ((source - target) * (1 - hit));
        int impactY = (int) y;
        Color color = ultimateAction ? GameTheme.GOLD : action == Battle.Action.POWER_STRIKE ? GameTheme.RED : accent;
        int size = ultimateAction ? 66 : action == Battle.Action.POWER_STRIKE ? 45 : 31;
        slash(g, impactX, impactY, size, hit, color, role == 1 ? 2 : 1);
        burst(g, impactX, impactY, size * .75, hit, color);
        if (ultimateAction) {
            magicCircle(g, impactX, impactY, size + 15, hit, color);
            particles(g, impactX, impactY, hit, color, 14, size + 32);
        }
    }
    private void slash(Graphics2D g, int x, int y, int size, double progress, Color color, int count) {
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (220 * (1 - progress * .45))));
        for (int i = 0; i < count; i++) {
            int offset = (i - (count - 1) / 2) * 13;
            g.drawArc(x - size + offset, y - size / 2, size * 2, size, i == 0 ? 200 : 160, 92);
        }
        g.setStroke(new BasicStroke(1));
    }
    private void burst(Graphics2D g, int x, int y, double radius, double progress, Color color) {
        int alpha = (int) (210 * (1 - progress));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
        g.setStroke(new BasicStroke(2));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double inner = radius * .22, outer = radius * (.60 + progress * .4);
            g.drawLine((int) (x + Math.cos(angle) * inner), (int) (y + Math.sin(angle) * inner), (int) (x + Math.cos(angle) * outer), (int) (y + Math.sin(angle) * outer));
        }
    }
    private void drain(Graphics2D g, int source, int target, double y, double progress, Color color) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 125)); g.setStroke(new BasicStroke(3));
        QuadCurve2D path = new QuadCurve2D.Double(source, y, (source + target) / 2.0, y - 85, target, y);
        g.draw(path);
        int x = (int) (source + (target - source) * progress), py = (int) (y - Math.sin(progress * Math.PI) * 85);
        g.setColor(color); g.fill(new Ellipse2D.Double(x - 7, py - 7, 14, 14)); burst(g, x, py, 18, progress * .7, color);
    }
    private void potion(Graphics2D g, int x, double y, double progress) {
        int alpha = (int) (220 * (1 - progress * .35));
        g.setColor(new Color(GameTheme.GREEN.getRed(), GameTheme.GREEN.getGreen(), GameTheme.GREEN.getBlue(), alpha));
        int bottleY = (int) (y - 35 - progress * 45);
        g.fillRoundRect(x - 8, bottleY, 16, 24, 6, 6); g.fillRect(x - 4, bottleY - 8, 8, 9);
        g.setColor(GameTheme.GOLD); g.drawRoundRect(x - 8, bottleY, 16, 24, 6, 6); g.drawLine(x - 4, bottleY - 8, x + 4, bottleY - 8);
        particles(g, x, bottleY + 10, progress, GameTheme.GREEN, 8, 32);
    }
    private void particles(Graphics2D g, int x, int y, double progress, Color color, int count, double radius) {
        int alpha = (int) (200 * (1 - progress * .6)); g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
        for (int i = 0; i < count; i++) { double angle = i * 2.39996; double distance = radius * (.25 + progress * .75); GameArt.diamond(g, x + Math.cos(angle) * distance, y + Math.sin(angle) * distance, i % 3 + 2); }
    }
    private void magicCircle(Graphics2D g, int x, int y, double radius, double progress, Color color) {
        int alpha = (int) (180 * (1 - progress)); g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha))); g.setStroke(new BasicStroke(2));
        g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        g.drawArc((int) (x - radius * 1.25), (int) (y - radius * 1.25), (int) (radius * 2.5), (int) (radius * 2.5), (int) (progress * 260), 170);
        g.setStroke(new BasicStroke(1));
    }
    private void shield(Graphics2D g, double x, double y, double height, double progress, Color color) {
        int radius = (int) (height * .21); int alpha = (int) (90 + 90 * Math.abs(Math.sin((tick + actionToken) * .08)));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha / 3)); g.fillOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha)); g.setStroke(new BasicStroke(3)); g.drawOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
        g.drawArc((int) x - radius - 8, (int) y - radius - 8, radius * 2 + 16, radius * 2 + 16, (int) (progress * 300), 170); g.setStroke(new BasicStroke(1));
    }
    private void defeatBurst(Graphics2D g, int x, double y, double height, double progress) { particles(g, x, (int) y, progress, GameTheme.RED, 10, height * .32); }
    private void celebration(Graphics2D g, int width, int height, double progress) { particles(g, width / 2, height / 2, progress, GameTheme.GOLD, 18, Math.min(width, height) * .35); }

    private void floating(Graphics2D g, int change, int x, int y, double progress) {
        if (change == 0) return;
        g.setColor(change > 0 ? GameTheme.GREEN : GameTheme.RED); g.setFont(GameTheme.font(Font.BOLD, 21)); center(g, (change > 0 ? "+" : "") + change, x, y - (int) (progress * 16));
    }
    private void center(Graphics2D g, String text, int x, int y) { g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, y); }
}

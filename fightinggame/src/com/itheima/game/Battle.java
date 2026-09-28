package com.itheima.game;

import com.itheima.doman.Character;
import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.HeroCharacter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Battle {
    public enum Action { ATTACK, POWER_STRIKE, DRAIN, DEFEND, POTION }
    public record TurnResult(boolean accepted, List<String> messages) {}

    private final HeroCharacter hero;
    private final EnemyCharacter enemy;
    private final Random random;
    private int drainCooldown;
    private int round = 1;
    private int energy;
    private boolean enemyUsesSkill;

    public Battle(HeroCharacter hero, EnemyCharacter enemy, Random random) {
        this.hero = hero;
        this.enemy = enemy;
        this.random = random;
        hero.clearDefense();
        enemyUsesSkill = random.nextBoolean();
    }

    public int getRound() { return round; }
    public int getDrainCooldown() { return drainCooldown; }
    public boolean isOver() { return !hero.isAlive() || !enemy.isAlive(); }
    public int getEnergy() { return energy; }
    public String getIntent() {
        if (!enemyUsesSkill) return "普通攻击";
        return switch (enemy.getSkill()) {
            case HEAVY_STRIKE -> "猛击 / 150%攻击";
            case DOUBLE_STRIKE -> "快速攻击 / 连击两次";
            case GUARD -> "防御姿态";
            case FIREBALL -> "火球术 / 180%攻击";
        };
    }

    public static int calculateDamage(int attack, int defense) {
        return Math.max(1, attack - defense);
    }

    public TurnResult play(Action action) {
        if (isOver()) return rejected("战斗已经结束。");
        CombatRules.Result result = CombatRules.play(hero, enemy, action, drainCooldown);
        if (!result.accepted()) return new TurnResult(false, result.messages());
        drainCooldown = result.drainCooldown();
        List<String> messages = new ArrayList<>(result.messages());
        energy = Math.min(100, energy + 25);
        if (enemy.isAlive()) enemyTurn(messages);
        round++;
        enemyUsesSkill = random.nextBoolean();
        return new TurnResult(true, List.copyOf(messages));
    }

    public TurnResult ultimate() {
        if (isOver()) return rejected("战斗已经结束。");
        if (energy < 100) return rejected("能量不足，大招需要100能量。");
        energy = 0;
        drainCooldown = Math.max(0, drainCooldown - 1);
        List<String> messages = new ArrayList<>();
        int multiplier = switch (hero.getStyle()) { case VANGUARD -> 14; case RAIDER -> 30; case MYSTIC -> 18; };
        hit(hero, enemy, hero.getAttack() * multiplier / 10, hero.getStyle().ultimate, messages);
        if (hero.getStyle() != HeroCharacter.Style.RAIDER) {
            messages.add("大招恢复 " + hero.heal(hero.getStyle() == HeroCharacter.Style.VANGUARD ? 45 : 40) + " HP。");
        }
        if (hero.getStyle() == HeroCharacter.Style.VANGUARD) hero.defend();
        if (enemy.isAlive()) enemyTurn(messages);
        round++;
        enemyUsesSkill = random.nextBoolean();
        return new TurnResult(true, List.copyOf(messages));
    }

    private TurnResult rejected(String message) {
        return new TurnResult(false, List.of(message));
    }

    private void enemyTurn(List<String> messages) {
        if (!enemyUsesSkill) {
            hit(enemy, hero, enemy.getAttack(), "普通攻击", messages);
            return;
        }
        switch (enemy.getSkill()) {
            case HEAVY_STRIKE -> hit(enemy, hero, enemy.getAttack() * 15 / 10, "猛击", messages);
            case DOUBLE_STRIKE -> {
                for (int i = 0; i < 2 && hero.isAlive(); i++) {
                    hit(enemy, hero, enemy.getAttack() / 2, "快速攻击 (" + (i + 1) + "/2)", messages);
                }
            }
            case GUARD -> {
                enemy.defend();
                messages.add(enemy.getName() + "进入防御姿态，下次受到的攻击伤害减半。");
            }
            case FIREBALL -> hit(enemy, hero, enemy.getAttack() * 18 / 10, "火球术", messages);
        }
    }

    private int hit(Character source, Character target, int power, String skill, List<String> messages) {
        int damage = target.takeDamage(calculateDamage(power, target.getDefense()));
        messages.add(source.getName() + "使用" + skill + "，对" + target.getName() + "造成 " + damage + " 点伤害。");
        return damage;
    }
}

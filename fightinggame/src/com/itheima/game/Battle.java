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

    public Battle(HeroCharacter hero, EnemyCharacter enemy, Random random) {
        this.hero = hero;
        this.enemy = enemy;
        this.random = random;
        hero.clearDefense();
    }

    public int getRound() { return round; }
    public int getDrainCooldown() { return drainCooldown; }
    public boolean isOver() { return !hero.isAlive() || !enemy.isAlive(); }

    public static int calculateDamage(int attack, int defense) {
        return Math.max(1, attack - defense);
    }

    public TurnResult play(Action action) {
        if (isOver()) return rejected("战斗已经结束。");
        if (action == null) return rejected("请选择有效行动。");
        if (action == Action.POWER_STRIKE && hero.getHP() <= 10) return rejected("生命不足，需要至少 11 HP。");
        if (action == Action.DRAIN && drainCooldown > 0) return rejected("生命汲取尚在冷却，请选择其他行动。");
        if (action == Action.POTION && hero.getPotions() == 0) return rejected("药水已用完。");
        if (action == Action.POTION && hero.getHP() == hero.getMaxHP()) return rejected("生命已满，无需使用药水。");

        List<String> messages = new ArrayList<>();
        if (drainCooldown > 0) drainCooldown--;
        switch (action) {
            case ATTACK -> hit(hero, enemy, hero.getAttack(), "普通攻击", messages);
            case POWER_STRIKE -> {
                hero.spendHealth(10);
                messages.add("强力一击消耗 10 HP。");
                hit(hero, enemy, hero.getAttack() * 18 / 10, "强力一击", messages);
            }
            case DRAIN -> {
                int damage = hit(hero, enemy, hero.getAttack() * 12 / 10, "生命汲取", messages);
                int restored = hero.heal(Math.max(1, damage / 2));
                messages.add("生命汲取恢复 " + restored + " HP。");
                drainCooldown = 2;
            }
            case DEFEND -> {
                hero.defend();
                messages.add("你进入防御姿态，下次受到的攻击伤害减半。");
            }
            case POTION -> messages.add("使用治疗药水，恢复 " + hero.usePotion() + " HP。");
        }
        if (enemy.isAlive()) enemyTurn(messages);
        round++;
        return new TurnResult(true, List.copyOf(messages));
    }

    private TurnResult rejected(String message) {
        return new TurnResult(false, List.of(message));
    }

    private void enemyTurn(List<String> messages) {
        if (!random.nextBoolean()) {
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

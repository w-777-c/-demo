package com.itheima.game;

import com.itheima.doman.Character;
import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.HeroCharacter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 单场战斗的权威规则协调器，负责玩家行动、敌方回应、能量和回合推进。 */
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
    private Action lastEnemyAction = Action.ATTACK;

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
    /** The action used by the most recent enemy response, for deterministic UI animation. */
    public Action getLastEnemyAction() { return lastEnemyAction; }
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

    /** 校验并执行一个玩家基础行动；非法行动不会推进回合或资源。 */
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

    /** 执行职业大招；能量不足时返回拒绝结果，不触发敌方回合。 */
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

    /** 根据本回合预告执行敌方动作，并记录给动画层使用的实际动作类型。 */
    private void enemyTurn(List<String> messages) {
        if (!enemyUsesSkill) {
            lastEnemyAction = Action.ATTACK;
            hit(enemy, hero, enemy.getAttack(), "普通攻击", messages);
            return;
        }
        switch (enemy.getSkill()) {
            case HEAVY_STRIKE -> { lastEnemyAction = Action.POWER_STRIKE; hit(enemy, hero, enemy.getAttack() * 15 / 10, "猛击", messages); }
            case DOUBLE_STRIKE -> {
                lastEnemyAction = Action.ATTACK;
                for (int i = 0; i < 2 && hero.isAlive(); i++) {
                    hit(enemy, hero, enemy.getAttack() / 2, "快速攻击 (" + (i + 1) + "/2)", messages);
                }
            }
            case GUARD -> {
                lastEnemyAction = Action.DEFEND;
                enemy.defend();
                messages.add(enemy.getName() + "进入防御姿态，下次受到的攻击伤害减半。");
            }
            case FIREBALL -> { lastEnemyAction = Action.DRAIN; hit(enemy, hero, enemy.getAttack() * 18 / 10, "火球术", messages); }
        }
    }

    private int hit(Character source, Character target, int power, String skill, List<String> messages) {
        int damage = target.takeDamage(calculateDamage(power, target.getDefense()));
        messages.add(source.getName() + "使用" + skill + "，对" + target.getName() + "造成 " + damage + " 点伤害。");
        return damage;
    }
}

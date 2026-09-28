package com.itheima.game;

import com.itheima.doman.Character;
import com.itheima.doman.HeroCharacter;
import java.util.ArrayList;
import java.util.List;

/** Resolves one player's action without scheduling an AI or another player's turn. */
public final class CombatRules {
    private CombatRules() {}
    public record Result(boolean accepted, int drainCooldown, List<String> messages) {}

    public static String unavailable(HeroCharacter actor, Battle.Action action, int cooldown) {
        if (!actor.isAlive()) return "角色已被击败。";
        if (action == null) return "请选择有效行动。";
        if (action == Battle.Action.POWER_STRIKE && actor.getHP() <= 10) return "生命不足，需要至少 11 HP。";
        if (action == Battle.Action.DRAIN && cooldown > 0) return "生命汲取尚在冷却，请选择其他行动。";
        if (action == Battle.Action.POTION && actor.getPotions() == 0) return "药水已用完。";
        if (action == Battle.Action.POTION && actor.getHP() == actor.getMaxHP()) return "生命已满，无需使用药水。";
        return "";
    }

    public static Result play(HeroCharacter actor, Character target, Battle.Action action, int cooldown) {
        String reason = unavailable(actor, action, cooldown);
        if (!target.isAlive()) reason = "战斗已经结束。";
        if (!reason.isEmpty()) return new Result(false, cooldown, List.of(reason));
        int nextCooldown = Math.max(0, cooldown - 1);
        List<String> messages = new ArrayList<>();
        switch (action) {
            case ATTACK -> hit(actor, target, actor.getAttack(), "普通攻击", messages);
            case POWER_STRIKE -> {
                actor.spendHealth(10);
                messages.add("强力一击消耗 10 HP。");
                hit(actor, target, actor.getAttack() * 18 / 10, "强力一击", messages);
            }
            case DRAIN -> {
                int damage = hit(actor, target, actor.getAttack() * 12 / 10, "生命汲取", messages);
                int restored = actor.heal(Math.max(1, damage / 2));
                messages.add("生命汲取恢复 " + restored + " HP。");
                nextCooldown = 2;
            }
            case DEFEND -> {
                actor.defend();
                messages.add(actor.getName() + "进入防御姿态，下次受到的攻击伤害减半。");
            }
            case POTION -> messages.add(actor.getName() + "使用治疗药水，恢复 " + actor.usePotion() + " HP。");
        }
        return new Result(true, nextCooldown, List.copyOf(messages));
    }

    private static int hit(Character source, Character target, int power, String skill, List<String> messages) {
        int damage = target.takeDamage(Battle.calculateDamage(power, target.getDefense()));
        messages.add(source.getName() + "使用" + skill + "，对" + target.getName() + "造成 " + damage + " 点伤害。");
        return damage;
    }
}

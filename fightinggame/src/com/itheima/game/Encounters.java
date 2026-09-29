package com.itheima.game;

import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.EnemyCharacter.Skill;
import java.util.Random;

/** 随远征进度生成独立敌人，避免上一场战斗的成长状态泄漏到下一场。 */
public final class Encounters {
    private Encounters() {}

    /** 根据胜场、挑战模式和随机源选择敌人类型及难度。 */
    public static EnemyCharacter create(int wins, boolean challenge, Random random) {
        if (wins < 0) throw new IllegalArgumentException("Negative wins");
        // Bound endless-mode scaling to keep combat arithmetic within int range.
        int scale = Math.min(wins, 10000);
        if (challenge && wins == 9) {
            return new EnemyCharacter("守关者", 220, 36, 18, Skill.FIREBALL);
        }
        return switch (random.nextInt(4)) {
            case 0 -> enemy("初级战士", 80, 15, 10, Skill.HEAVY_STRIKE, scale);
            case 1 -> enemy("敏捷刺客", 60, 20, 5, Skill.DOUBLE_STRIKE, scale);
            case 2 -> enemy("重装坦克", 120, 10, 20, Skill.GUARD, scale);
            default -> enemy("神秘法师", 70, 25, 8, Skill.FIREBALL, scale);
        };
    }

    private static EnemyCharacter enemy(String name, int hp, int atk, int def, Skill skill, int scale) {
        return new EnemyCharacter(name, hp + scale * 10, atk + scale * 2, def + scale, skill);
    }
}

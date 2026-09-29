package com.itheima.doman;

/** 带有敌方 AI 技能类型的角色模型。敌人属性由每场遭遇独立创建。 */
public class EnemyCharacter extends Character {
    public enum Skill { HEAVY_STRIKE, DOUBLE_STRIKE, GUARD, FIREBALL }

    private final Skill skill;

    public EnemyCharacter(String name, int hp, int attack, int defense, Skill skill) {
        super(name, hp, attack, defense);
        if (skill == null) throw new IllegalArgumentException("Missing enemy skill");
        this.skill = skill;
    }

    public Skill getSkill() { return skill; }
}

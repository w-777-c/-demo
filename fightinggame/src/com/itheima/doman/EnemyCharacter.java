package com.itheima.doman;
//表示敌人游戏人物的角色
public class EnemyCharacter extends Character {
    public String skill;
    public boolean defending;//当前游戏人物是否拥有减少伤害的状态
    public EnemyCharacter() {
        super();
    }

    public EnemyCharacter(String name, int HP, int attack, int defense, String skill) {
        super(name, HP, attack, defense);
        this.skill = skill;
    }

    //重写父类的takeDamage方法
    @Override
    public void takeDamage(int damage) {
        //如果处于防御状态，伤害减半
        //true：处于防御状态，false：不处于防御状态
        if (defending) {
            damage = damage/2 > 1 ? damage/2 : 1;
            defending = false;
        }
        //调用父类的takeDamage方法
        super.takeDamage(damage);
    }
}

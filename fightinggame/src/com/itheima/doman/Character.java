package com.itheima.doman;

public class Character {
    public String name;
    public int HP;
    public int maxHP;
    public int attack;
    public int defense;

    public Character() {

    }

    //刚创建人物的时候，血量是满的
    public Character(String name, int HP, int attack, int defense) {
        this.name = name;
        this.HP = HP;
        this.maxHP = HP;
        this.attack = attack;
        this.defense = defense;
    }

    //1.判断当前人物是否还活着
    public boolean isAlive() {
        return HP > 0;
    }

    //2，恢复血量
    //amount：具体回多少血
    //作用：回复血量
    //形参：具体回多少血
    public void heal(int amount) {
        HP += amount;
        if (HP > maxHP) {
            HP = maxHP;
        }
    }

    //3.受到伤害
    //damage：具体受多少伤
    //作用：受到了N点伤害之后，还有多少点血
    //形参：具体收到了多少伤害
    public void takeDamage(int damage) {
        HP -= damage;
        if (HP < 0) {
            HP = 0;
        }
    }

    //4.展示人物的属性
    public String show() {
        return (name + "[当前血量：" + HP + "\t攻击：" + attack + "\t防御：" + defense + "]");
    }

}

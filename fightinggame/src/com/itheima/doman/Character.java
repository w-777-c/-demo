package com.itheima.doman;

public class Character {
    private final String name;
    private int hp;
    private int maxHP;
    private int attack;
    private int defense;
    private boolean defending;

    public Character(String name, int hp, int attack, int defense) {
        if (name == null || name.isBlank() || hp <= 0 || attack < 0 || defense < 0) {
            throw new IllegalArgumentException("Invalid character attributes");
        }
        this.name = name;
        this.hp = hp;
        this.maxHP = hp;
        this.attack = attack;
        this.defense = defense;
    }

    public String getName() { return name; }
    public int getHP() { return hp; }
    public int getMaxHP() { return maxHP; }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public boolean isAlive() { return hp > 0; }
    public boolean isDefending() { return defending; }
    public void defend() { defending = true; }
    public void clearDefense() { defending = false; }

    public int heal(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative healing");
        if (!isAlive()) return 0;
        int actual = Math.min(amount, maxHP - hp);
        hp += actual;
        return actual;
    }

    public int takeDamage(int damage) {
        if (damage < 0) throw new IllegalArgumentException("Negative damage");
        if (damage == 0 || !isAlive()) return 0;
        if (defending) {
            damage = Math.max(1, damage / 2);
            defending = false;
        }
        int actual = Math.min(hp, damage);
        hp -= actual;
        return actual;
    }

    public boolean spendHealth(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative health cost");
        if (hp <= amount) return false;
        hp -= amount;
        return true;
    }

    protected void grow(int health, int power, int armor) {
        maxHP += health;
        if (isAlive()) hp += health;
        attack += power;
        defense += armor;
    }

    public String show() {
        return name + " [HP: " + hp + "/" + maxHP + ", ATK: " + attack + ", DEF: " + defense + "]";
    }
}

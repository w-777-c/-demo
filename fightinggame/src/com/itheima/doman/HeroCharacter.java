package com.itheima.doman;

public class HeroCharacter extends Character {
    private int level = 1;
    private int potions = 3;

    public HeroCharacter(String name, int hp, int attack, int defense) {
        super(name, hp, attack, defense);
    }

    public static HeroCharacter create(String name, int health, int power, int armor) {
        if (health < 0 || power < 0 || armor < 0 || (long) health + power + armor != 20) {
            throw new IllegalArgumentException("Exactly 20 attribute points are required");
        }
        return new HeroCharacter(name, 100 + health * 10, 10 + power * 2, armor);
    }

    public int getLevel() { return level; }
    public int getPotions() { return potions; }

    public void levelUp() {
        level++;
        grow(30, 5, 3);
        potions = Math.min(3, potions + 1);
    }

    public int usePotion() {
        if (potions == 0 || !isAlive() || getHP() == getMaxHP()) return 0;
        potions--;
        return heal(50);
    }

    public String showSkill() {
        return "普通攻击、强力一击、生命汲取、防御、治疗药水";
    }
}

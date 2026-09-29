package com.itheima.doman;

/** 玩家角色模型，包含职业、等级、药水和局内成长数据。 */
public class HeroCharacter extends Character {
    /** 三个可玩的职业预设；数值同时用于创建角色和图鉴展示。 */
    public enum Style {
        VANGUARD("铁卫", "不屈壁垒", "140%攻击，恢复45生命并防御", 9, 6, 5, 3),
        RAIDER("狂刃", "破阵斩", "300%攻击", 4, 16, 0, 2),
        MYSTIC("灵术师", "灵魂潮汐", "180%攻击，恢复40生命", 8, 10, 2, 4);
        public final String title, ultimate, description;
        public final int health, power, armor, appearance;
        Style(String title, String ultimate, String description, int health, int power, int armor, int appearance) {
            this.title = title; this.ultimate = ultimate; this.description = description;
            this.health = health; this.power = power; this.armor = armor; this.appearance = appearance;
        }
        @Override public String toString() { return title; }
    }
    private int level = 1;
    private int potions = 3;
    private Style style = Style.VANGUARD;

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
    public Style getStyle() { return style; }
    public static HeroCharacter create(String name, Style style) {
        HeroCharacter hero = create(name, style.health, style.power, style.armor);
        hero.style = style;
        return hero;
    }
    public void train(int health, int power, int armor) {
        if (health < 0 || power < 0 || armor < 0) throw new IllegalArgumentException("Negative training");
        grow(health, power, armor);
    }
    public void refillPotion() { potions = Math.min(3, potions + 1); }

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

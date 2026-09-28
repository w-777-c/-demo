package com.itheima.game;

import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.HeroCharacter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Collections;

public final class GameSession {
    public enum State { FIGHTING, RESTING, FINISHED }
    private final HeroCharacter hero;
    private final boolean challenge;
    private final Random random;
    private EnemyCharacter enemy;
    private Battle battle;
    private int wins;
    private State state;
    private final boolean expedition;
    private List<Reward> rewards = List.of();
    private final List<String> upgrades = new ArrayList<>();
    public enum Reward {
        BLADE("磨砺锋刃", "攻击 +4"), ARMOR("精钢护甲", "防御 +2"),
        VITALITY("生命符石", "生命上限与当前生命 +20"),
        SUPPLY("补给箱", "药水 +1（上限3），恢复15生命"), REST("营地疗伤", "恢复65生命");
        public final String title, description;
        Reward(String title, String description) { this.title = title; this.description = description; }
        @Override public String toString() { return title; }
    }

    public GameSession(HeroCharacter hero, boolean challenge, Random random) {
        this(hero, challenge, random, false);
    }

    public GameSession(HeroCharacter hero, boolean challenge, Random random, boolean expedition) {
        this.hero = hero;
        this.challenge = challenge;
        this.random = random;
        this.expedition = expedition;
        startBattle();
    }

    public HeroCharacter getHero() { return hero; }
    public EnemyCharacter getEnemy() { return enemy; }
    public Battle getBattle() { return battle; }
    public int getWins() { return wins; }
    public boolean isChallenge() { return challenge; }
    public boolean isCleared() { return challenge && wins == 10; }
    public State getState() { return state; }
    public List<Reward> getRewards() { return rewards; }
    public List<String> getUpgrades() { return List.copyOf(upgrades); }
    public boolean isElite() { return expedition && (wins + 1) % 3 == 0; }
    public String getRegion() { return wins < 3 ? "城门遗迹" : wins < 6 ? "熔炉回廊" : wins < 9 ? "王城禁卫" : "王座大厅"; }

    public String chooseReward(Reward reward) {
        if (state != State.RESTING || !rewards.contains(reward)) throw new IllegalStateException("Reward unavailable");
        switch (reward) {
            case BLADE -> hero.train(0, 4, 0);
            case ARMOR -> hero.train(0, 0, 2);
            case VITALITY -> hero.train(20, 0, 0);
            case SUPPLY -> { hero.refillPotion(); hero.heal(15); }
            case REST -> hero.heal(65);
        }
        rewards = List.of();
        upgrades.add(reward.title);
        return "获得「" + reward.title + "」：" + reward.description + "。";
    }

    public List<String> play(Battle.Action action) {
        if (state != State.FIGHTING) return List.of();
        return settle(battle.play(action));
    }

    public List<String> ultimate() {
        if (!expedition || state != State.FIGHTING) return List.of();
        return settle(battle.ultimate());
    }

    private List<String> settle(Battle.TurnResult result) {
        List<String> messages = new ArrayList<>(result.messages());
        if (!result.accepted() || !battle.isOver()) return messages;
        if (!hero.isAlive()) {
            state = State.FINISHED;
            messages.add("你被击败了。本局胜场：" + wins + "。");
            return messages;
        }
        wins++;
        messages.add("击败" + enemy.getName() + "！累计 " + wins + " 胜。");
        if (isCleared()) {
            state = State.FINISHED;
            messages.add("十关挑战通关！你成为了竞技场冠军。");
            return messages;
        }
        if (wins % 3 == 0) {
            hero.levelUp();
            messages.add("升级至 Lv." + hero.getLevel() + "：生命 +30，攻击 +5，防御 +3，补充药水。");
        }
        messages.add("战后休息恢复 " + hero.heal(20 + random.nextInt(21)) + " HP。");
        hero.clearDefense();
        state = State.RESTING;
        if (expedition) {
            List<Reward> options = new ArrayList<>(List.of(Reward.values()));
            Collections.shuffle(options, random);
            rewards = List.copyOf(options.subList(0, 3));
        }
        return messages;
    }

    public void nextBattle() {
        if (state != State.RESTING) throw new IllegalStateException("Not resting");
        if (!rewards.isEmpty()) throw new IllegalStateException("Choose a reward first");
        startBattle();
    }

    public void retire() { state = State.FINISHED; }

    private void startBattle() {
        enemy = Encounters.create(wins, challenge, random);
        if (isElite()) enemy = new EnemyCharacter("精英·" + enemy.getName(), enemy.getMaxHP() + 40,
                enemy.getAttack() + 3, enemy.getDefense() + 2, enemy.getSkill());
        battle = new Battle(hero, enemy, random);
        state = State.FIGHTING;
    }
}

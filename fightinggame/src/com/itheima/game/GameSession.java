package com.itheima.game;

import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.HeroCharacter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GameSession {
    public enum State { FIGHTING, RESTING, FINISHED }
    private final HeroCharacter hero;
    private final boolean challenge;
    private final Random random;
    private EnemyCharacter enemy;
    private Battle battle;
    private int wins;
    private State state;

    public GameSession(HeroCharacter hero, boolean challenge, Random random) {
        this.hero = hero;
        this.challenge = challenge;
        this.random = random;
        startBattle();
    }

    public HeroCharacter getHero() { return hero; }
    public EnemyCharacter getEnemy() { return enemy; }
    public Battle getBattle() { return battle; }
    public int getWins() { return wins; }
    public boolean isChallenge() { return challenge; }
    public boolean isCleared() { return challenge && wins == 10; }
    public State getState() { return state; }

    public List<String> play(Battle.Action action) {
        if (state != State.FIGHTING) return List.of();
        Battle.TurnResult result = battle.play(action);
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
        return messages;
    }

    public void nextBattle() {
        if (state != State.RESTING) throw new IllegalStateException("Not resting");
        startBattle();
    }

    public void retire() { state = State.FINISHED; }

    private void startBattle() {
        enemy = Encounters.create(wins, challenge, random);
        battle = new Battle(hero, enemy, random);
        state = State.FIGHTING;
    }
}

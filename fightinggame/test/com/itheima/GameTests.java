package com.itheima;

import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.EnemyCharacter.Skill;
import com.itheima.doman.HeroCharacter;
import com.itheima.doman.User;
import com.itheima.game.Battle;
import com.itheima.game.Battle.Action;
import com.itheima.game.Encounters;
import com.itheima.game.GameSession;
import com.itheima.storage.Passwords;
import com.itheima.storage.UserStore;
import com.itheima.ui.ConsoleInput;
import com.itheima.ui.FightingGame;
import com.itheima.ui.Login;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Scanner;

/** 单机核心回归测试：覆盖角色属性、战斗规则、远征流程、账号存储和控制台交互。 */
public final class GameTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // 按模块顺序执行，任一断言失败都会让脚本以非零状态退出。
        characters(); battle(); expeditions(); sessions(); playability(); accounts(); console();
        System.out.println("PASS: " + checks + " assertions");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }

    private static void expeditions() {
        for (HeroCharacter.Style style : HeroCharacter.Style.values()) {
            HeroCharacter hero = HeroCharacter.create("Hero", style);
            EnemyCharacter opponent = enemy(2000, 1, Skill.GUARD);
            Battle battle = new Battle(hero, opponent, fixed(false));
            check(!battle.ultimate().accepted() && battle.getRound() == 1, "ultimate needs energy");
            check(!battle.play(Action.POTION).accepted() && battle.getEnergy() == 0, "invalid skill earns no energy");
            for (int i = 0; i < 4; i++) battle.play(Action.ATTACK);
            check(battle.getEnergy() == 100, "four valid actions charge ultimate");
            int hp = opponent.getHP();
            check(battle.ultimate().accepted(), "class ultimate accepted");
            int multiplier = style == HeroCharacter.Style.VANGUARD ? 14 : style == HeroCharacter.Style.RAIDER ? 30 : 18;
            check(hp - opponent.getHP() == hero.getAttack() * multiplier / 10, "class ultimate damage");
            check(battle.getEnergy() == 0 && !battle.ultimate().accepted(), "ultimate consumes energy once");
            check(battle.getIntent().equals("普通攻击"), "telegraphed action matches fixed AI");
        }
        GameSession session = new GameSession(new HeroCharacter("Hero", 5000, 1000, 100), true, new Random(3), true);
        for (int win = 1; win <= 10; win++) {
            session.play(Action.ATTACK);
            check(session.getWins() == win, "expedition win counted");
            if (win == 10) break;
            check(session.getRewards().size() == 3 && session.getRewards().stream().distinct().count() == 3, "three distinct rewards");
            try { session.nextBattle(); throw new AssertionError("reward must be chosen"); } catch (IllegalStateException expected) { checks++; }
            GameSession.Reward reward = session.getRewards().get(0);
            session.chooseReward(reward);
            try { session.chooseReward(reward); throw new AssertionError("reward claimed twice"); } catch (IllegalStateException expected) { checks++; }
            session.nextBattle();
            check(session.getEnemy().getName().startsWith("精英") == ((win + 1) % 3 == 0), "elite stages 3, 6, 9");
            check(session.getBattle().getEnergy() == 0, "new battle resets ultimate energy");
        }
        check(session.isCleared() && session.getRewards().isEmpty(), "boss clears expedition without extra reward");
        check(session.getUpgrades().size() == 9, "nine camp upgrades recorded");
    }

    private static Random fixed(boolean skill) {
        return new Random(0) { @Override public boolean nextBoolean() { return skill; } };
    }

    private static EnemyCharacter enemy(int hp, int attack, Skill skill) {
        return new EnemyCharacter("Enemy", hp, attack, 0, skill);
    }

    private static void characters() {
        HeroCharacter hero = HeroCharacter.create("Hero", 8, 10, 2);
        check(hero.getHP() == 180 && hero.getAttack() == 30 && hero.getDefense() == 2, "allocation");
        hero.takeDamage(50);
        hero.levelUp();
        check(hero.getMaxHP() == 210 && hero.getHP() == 160, "upgrade grows max and current health");
        check(hero.getAttack() == 35 && hero.getDefense() == 5, "upgrade stats");
        check(hero.heal(999) == 50 && hero.getHP() == 210, "heal reports actual amount");
        check(hero.usePotion() == 0 && hero.getPotions() == 3, "full health preserves potion");
        hero.defend();
        check(hero.takeDamage(0) == 0 && hero.isDefending(), "zero damage preserves guard");
        check(hero.takeDamage(11) == 5 && !hero.isDefending(), "guard halves one hit");
        check(hero.takeDamage(11) == 11, "next hit has full damage");
        check(hero.usePotion() == 16 && hero.getPotions() == 2, "potion heals actual missing HP");
        hero.defend();
        check(hero.spendHealth(10) && hero.getHP() == 200 && hero.isDefending(), "guard does not discount skill cost");
        hero.clearDefense(); hero.takeDamage(999);
        check(hero.getHP() == 0 && hero.heal(10) == 0, "dead heroes cannot revive through heal");
        try { hero.takeDamage(-1); throw new AssertionError("negative damage"); } catch (IllegalArgumentException expected) { checks++; }
        try { HeroCharacter.create("X", 20, 1, 0); throw new AssertionError("over-allocation"); } catch (IllegalArgumentException expected) { checks++; }
    }

    private static void battle() {
        HeroCharacter hero = new HeroCharacter("Hero", 100, 20, 0);
        hero.takeDamage(50);
        EnemyCharacter enemy = enemy(200, 10, Skill.HEAVY_STRIKE);
        enemy.defend();
        Battle battle = new Battle(hero, enemy, fixed(false));
        check(battle.play(Action.DRAIN).accepted(), "drain accepted");
        check(enemy.getHP() == 188 && hero.getHP() == 46, "drain uses actual guarded damage");
        int round = battle.getRound();
        check(!battle.play(Action.DRAIN).accepted() && battle.getRound() == round && hero.getHP() == 46, "rejected skill consumes no turn");
        battle.play(Action.ATTACK); battle.play(Action.DEFEND);
        check(battle.getDrainCooldown() == 0, "cooldown after two valid turns");
        check(battle.play(Action.DRAIN).accepted(), "drain available again");

        HeroCharacter weak = new HeroCharacter("Weak", 10, 20, 0);
        Battle blocked = new Battle(weak, enemy(100, 10, Skill.FIREBALL), fixed(false));
        check(!blocked.play(Action.POWER_STRIKE).accepted() && weak.getHP() == 10, "low HP skill rejected safely");
        check(!blocked.play(Action.POTION).accepted() && weak.getPotions() == 3, "full potion rejected without turn");
        blocked.play(Action.ATTACK);
        check(blocked.isOver() && !blocked.play(Action.ATTACK).accepted(), "dead hero cannot act");

        HeroCharacter killer = new HeroCharacter("Killer", 100, 100, 0);
        Battle lethal = new Battle(killer, enemy(2, 99, Skill.FIREBALL), fixed(true));
        lethal.play(Action.ATTACK);
        check(killer.getHP() == 100, "killed enemy never retaliates");
        HeroCharacter defender = new HeroCharacter("Defender", 100, 10, 0);
        new Battle(defender, enemy(100, 20, Skill.DOUBLE_STRIKE), fixed(true)).play(Action.DEFEND);
        check(defender.getHP() == 85, "guard protects first hit of double strike only");
        HeroCharacter tankTarget = new HeroCharacter("Target", 100, 20, 0);
        EnemyCharacter tank = enemy(100, 20, Skill.GUARD);
        Battle tankBattle = new Battle(tankTarget, tank, fixed(true));
        tankBattle.play(Action.ATTACK); tankBattle.play(Action.ATTACK);
        check(tank.getHP() == 70 && tankTarget.getHP() == 100, "enemy guard mitigates next attack");
        check(Battle.calculateDamage(1, 999) == 1, "minimum damage");
    }

    private static void sessions() {
        GameSession challenge = new GameSession(new HeroCharacter("Champion", 10000, 1000, 100), true, new Random(5));
        for (int i = 1; i <= 10; i++) {
            challenge.play(Action.ATTACK);
            check(challenge.getWins() == i, "win counted once " + i);
            challenge.play(Action.ATTACK);
            check(challenge.getWins() == i, "resting cannot repeat rewards " + i);
            if (i < 10) challenge.nextBattle();
        }
        check(challenge.isCleared() && challenge.getState() == GameSession.State.FINISHED, "ten-stage clear");
        check(challenge.getHero().getLevel() == 4, "upgrade every three wins");
        check(challenge.getHero().getMaxHP() == 10090, "three health upgrades");
        check(Encounters.create(9, true, new Random()).getName().equals("守关者"), "boss on final stage");
        EnemyCharacter a = Encounters.create(0, false, new Random(1));
        a.takeDamage(99);
        EnemyCharacter b = Encounters.create(1, false, new Random(1));
        check(b.getHP() == b.getMaxHP() && b.getMaxHP() == a.getMaxHP() + 10, "fresh scaled enemy");
        GameSession endless = new GameSession(new HeroCharacter("Endless", 10000, 1000, 100), false, new Random(5));
        for (int i = 0; i < 11; i++) { endless.play(Action.ATTACK); endless.nextBattle(); }
        check(endless.getWins() == 11 && !endless.isCleared(), "endless passes ten wins");
        endless.retire(); check(endless.getState() == GameSession.State.FINISHED, "retirement");
    }

    private static void playability() {
        for (int preset = 0; preset < 2; preset++) {
            int clears = 0;
            for (int seed = 0; seed < 100; seed++) {
                HeroCharacter hero = HeroCharacter.create("Player", preset == 0 ? 8 : 4, preset == 0 ? 10 : 16, preset == 0 ? 2 : 0);
                GameSession session = new GameSession(hero, true, new Random(seed));
                int actions = 0;
                while (session.getState() != GameSession.State.FINISHED && actions++ < 1000) {
                    if (session.getState() == GameSession.State.RESTING) { session.nextBattle(); continue; }
                    Action action;
                    if (hero.getHP() < 70 && hero.getPotions() > 0) action = Action.POTION;
                    else if (session.getBattle().getDrainCooldown() == 0) action = Action.DRAIN;
                    else if (hero.getHP() > 50) action = Action.POWER_STRIKE;
                    else action = Action.ATTACK;
                    session.play(action);
                }
                if (session.isCleared()) clears++;
            }
            check(clears > 0, "preset " + preset + " can complete challenge with legal actions");
            System.out.println("Preset " + preset + ": " + clears + "/100 clears with a fixed test strategy");
        }
    }

    private static void accounts() throws Exception {
        Path dir = Files.createTempDirectory("arena-test-");
        Path file = dir.resolve("accounts.properties");
        try {
            UserStore store = new UserStore(file);
            check(!UserStore.validUsername("123") && UserStore.validUsername("Hero1"), "username validation");
            check(!UserStore.validPassword("abcdef") && UserStore.validPassword("abc123"), "password validation");
            User user = store.register("Hero1", "abc123");
            check(!Files.readString(file).contains("abc123"), "no plaintext password on disk");
            check(Passwords.verify("abc123", user.getPasswordHash()), "password verifies");
            check(!Passwords.verify("abc124", user.getPasswordHash()), "wrong password rejected");
            check(!Passwords.hash("abc123").equals(user.getPasswordHash()), "random salt");
            user.recordGame(10, true); user.recordGame(3, false); store.save();
            User restored = new UserStore(file).find("Hero1");
            check(restored.getGames() == 2 && restored.getBestWins() == 10 && restored.getTotalWins() == 13 && restored.getClears() == 1, "statistics survive restart");
            try { store.register("Hero1", "abc123"); throw new AssertionError("duplicate username"); } catch (IllegalArgumentException expected) { checks++; }
            Files.writeString(file, "version=1\ncount=1\n");
            try { new UserStore(file); throw new AssertionError("corrupt store"); } catch (IOException expected) { checks++; }
            check(Files.readString(file).equals("version=1\ncount=1\n"), "corrupt file preserved");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
    }

    private static void console() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ConsoleInput input = new ConsoleInput(new Scanner("abc\n-1\n999999999999999\n2\nwrong\ny\n"), out);
        check(input.number("", 0, 3) == 2, "bad numeric input retried");
        check(input.confirm(""), "bad confirmation retried");
        try { input.read(""); throw new AssertionError("EOF"); } catch (ConsoleInput.EndOfInput expected) { checks++; }
        check(FightingGame.healthBar("X", 500, 100).endsWith("100/100 HP"), "health bar clamps overflow");
        Path dir = Files.createTempDirectory("arena-console-");
        try {
            ConsoleInput script = new ConsoleInput(new Scanner("3\n1\n1\n0\ny\n0\n"), out);
            new Login(script, out, new UserStore(dir.resolve("accounts.properties")), new Random(1)).start();
            check(bytes.toString(StandardCharsets.UTF_8).contains("本局结算"), "guest game returns to menu");
            ConsoleInput eof = new ConsoleInput(new Scanner("1\n1\n"), out);
            check(new FightingGame(eof, out, new Random(1)).gameStart("Guest").wins() == 0, "EOF settles active game");
        } finally { Files.deleteIfExists(dir); }
    }
}

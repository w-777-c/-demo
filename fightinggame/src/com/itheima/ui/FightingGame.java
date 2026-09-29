package com.itheima.ui;

import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.HeroCharacter;
import com.itheima.game.Battle;
import com.itheima.game.GameSession;
import java.io.PrintStream;
import java.util.Random;

/** 控制台战斗流程，复用桌面版的 GameSession 和 Battle 规则。 */
public final class FightingGame {
    public record Result(int wins, boolean cleared) {}

    private final ConsoleInput input;
    private final PrintStream out;
    private final Random random;

    public FightingGame(ConsoleInput input, PrintStream out, Random random) {
        this.input = input;
        this.out = out;
        this.random = random;
    }

    public Result gameStart(String username) {
        out.println("\n=== " + username + "的竞技场 ===");
        int mode = input.number("1. 十关挑战  2. 无尽试炼  0. 返回\n请选择：", 0, 2);
        if (mode == 0) return null;
        HeroCharacter player = createPlayerCharacter(username);
        GameSession session = new GameSession(player, mode == 1, random);
        try {
            while (session.getState() != GameSession.State.FINISHED) {
                EnemyCharacter enemy = session.getEnemy();
                out.println("\n=== 第 " + (session.getWins() + 1) + " 场" + (session.isChallenge() ? " / 10" : "") + " ===");
                out.println("对手：" + enemy.show());
                Battle battle = session.getBattle();
                while (session.getState() == GameSession.State.FIGHTING) {
                    out.println("\n第 " + battle.getRound() + " 回合 | 等级 " + player.getLevel());
                    out.println(healthBar(player.getName(), player.getHP(), player.getMaxHP()));
                    out.println(healthBar(enemy.getName(), enemy.getHP(), enemy.getMaxHP()));
                    if (player.isDefending()) out.println("你的防御姿态生效中。");
                    if (enemy.isDefending()) out.println("敌人的防御姿态生效中。");
                    out.println("1. 普通攻击   2. 强力一击 (10 HP / 180%攻击)");
                    out.println("3. 生命汲取 (120%攻击，回复实际伤害的一半，最低1点；冷却 " + battle.getDrainCooldown() + " 回合)");
                    out.println("4. 防御   5. 治疗药水 (+50 HP，剩余 " + player.getPotions() + ")   0. 撤退结算");
                    int action = input.number("请选择行动：", 0, 5);
                    if (action == 0) {
                        if (input.confirm("结束本局并结算？(y/n)：")) { session.retire(); break; }
                        continue;
                    }
                    session.play(Battle.Action.values()[action - 1]).forEach(out::println);
                }
                if (session.getState() == GameSession.State.RESTING) {
                    out.println(player.show());
                    if (input.confirm("继续下一场？(y/n)：")) session.nextBattle();
                    else session.retire();
                }
            }
        } catch (ConsoleInput.EndOfInput exception) {
            out.println("\n输入结束，结算已完成的战斗。");
            session.retire();
        }
        out.println("\n=== 本局结算 ===");
        out.println(session.isCleared() ? "十关挑战通关！" : player.isAlive() ? "已结束本次挑战。" : "你被击败了。");
        out.println("胜场：" + session.getWins() + " | 等级：" + player.getLevel());
        return new Result(session.getWins(), session.isCleared());
    }

    public HeroCharacter createPlayerCharacter(String username) {
        out.println("\n分配20点属性：每点生命 +10、攻击 +2、防御 +1。");
        int preset = input.number("1. 均衡 (8/10/2)  2. 猛攻 (4/16/0)  3. 自定义\n请选择：", 1, 3);
        int health = preset == 1 ? 8 : 4;
        int power = preset == 1 ? 10 : 16;
        if (preset == 3) {
            health = input.number("生命点数 (0-20)：", 0, 20);
            power = input.number("攻击点数 (0-" + (20 - health) + ")：", 0, 20 - health);
            out.println("剩余 " + (20 - health - power) + " 点分配给防御。");
        }
        HeroCharacter player = HeroCharacter.create(username, health, power, 20 - health - power);
        out.println("角色创建成功：" + player.show());
        return player;
    }

    public static String healthBar(String name, int hp, int maxHP) {
        int safeMax = Math.max(1, maxHP);
        int safeHP = Math.max(0, Math.min(hp, safeMax));
        int filled = (int) ((long) safeHP * 20 / safeMax);
        return name + " [" + "#".repeat(filled) + "-".repeat(20 - filled) + "] " + safeHP + "/" + safeMax + " HP";
    }
}

package com.itheima.ui;

import com.itheima.doman.User;
import com.itheima.storage.Passwords;
import com.itheima.storage.UserStore;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

/** 控制台登录、注册和离线账号菜单。 */
public final class Login {
    private final ConsoleInput input;
    private final PrintStream out;
    private final UserStore store;
    private final Random random;

    public Login(ConsoleInput input, PrintStream out, UserStore store, Random random) {
        this.input = input;
        this.out = out;
        this.store = store;
        this.random = random;
    }

    public void start() {
        try {
            while (true) {
                out.println("\n=== 铁境竞技场 ===");
                int choice = input.number("1. 登录  2. 注册  3. 游客试玩  4. 战绩榜  0. 退出\n请选择：", 0, 4);
                if (choice == 0) return;
                if (choice == 4) { leaderboard(); continue; }
                if (choice == 3) { play(null); continue; }
                String name = input.read("用户名 (3-16位字母数字，至少一个字母)：");
                String password = input.read("密码 (6-64位，包含字母和数字)：");
                if (choice == 2) {
                    if (!password.equals(input.read("确认密码："))) { out.println("两次密码不一致。"); continue; }
                    try {
                        store.register(name, password);
                        out.println("注册成功，账号已保存。");
                    } catch (IllegalArgumentException exception) {
                        out.println("用户名已存在或用户名、密码格式不符合要求。");
                    } catch (IOException exception) { out.println("保存失败：" + exception.getMessage()); }
                } else {
                    User user = store.find(name);
                    if (user != null && Passwords.verify(password, user.getPasswordHash())) play(user);
                    else out.println("用户名或密码错误。");
                }
            }
        } catch (ConsoleInput.EndOfInput exception) { out.println("\n输入结束，已退出。"); }
    }

    private void play(User user) {
        FightingGame.Result result = new FightingGame(input, out, random).gameStart(user == null ? "游客" : user.getUsername());
        if (user != null && result != null) {
            user.recordGame(result.wins(), result.cleared());
            try { store.save(); }
            catch (IOException exception) { out.println("战绩保存失败：" + exception.getMessage()); }
        }
    }

    private void leaderboard() {
        out.println("玩家 | 最高胜场 | 总胜场 | 局数 | 通关次数");
        store.all().stream().sorted(java.util.Comparator.comparingInt(User::getBestWins).reversed()
                .thenComparing(User::getUsername)).limit(10).forEach(user ->
                out.println(user.getUsername() + " | " + user.getBestWins() + " | " + user.getTotalWins()
                        + " | " + user.getGames() + " | " + user.getClears()));
    }
}

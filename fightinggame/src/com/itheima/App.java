package com.itheima;

import com.itheima.storage.UserStore;
import com.itheima.ui.ConsoleInput;
import com.itheima.ui.GameFrame;
import com.itheima.ui.Login;
import java.awt.GraphicsEnvironment;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {
    public static void main(String[] args) {
        boolean console = Arrays.asList(args).contains("--console") || GraphicsEnvironment.isHeadless();
        Path directory = Path.of(System.getProperty("fightinggame.dataDir", "data"));
        try {
            Files.createDirectories(directory);
            FileChannel channel = FileChannel.open(directory.resolve("game.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new java.io.IOException("该数据目录已有游戏运行，请先关闭另一个窗口。");
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { lock.release(); channel.close(); } catch (java.io.IOException ignored) { }
            }));
            UserStore store = new UserStore(directory.resolve("accounts.properties"));
            if (console) {
                new Login(new ConsoleInput(new Scanner(System.in), System.out), System.out, store, new Random()).start();
            } else {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                SwingUtilities.invokeLater(() -> new GameFrame(store, new Random()).setVisible(true));
            }
        } catch (Exception exception) {
            String message = "启动失败：" + exception.getMessage();
            System.err.println(message);
            if (!console) JOptionPane.showMessageDialog(null, message, "铁境竞技场", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}

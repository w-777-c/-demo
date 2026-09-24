package com.itheima.ui;

import java.io.PrintStream;
import java.util.Scanner;

public final class ConsoleInput {
    public static final class EndOfInput extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleInput(Scanner scanner, PrintStream out) {
        this.scanner = scanner;
        this.out = out;
    }

    public String read(String prompt) {
        out.print(prompt);
        if (!scanner.hasNextLine()) throw new EndOfInput();
        return scanner.nextLine().trim();
    }

    public int number(String prompt, int min, int max) {
        while (true) {
            String value = read(prompt);
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= min && parsed <= max) return parsed;
            } catch (NumberFormatException ignored) {
                // Invalid input is retried without consuming a game action.
            }
            out.println("请输入 " + min + " 到 " + max + " 之间的整数。");
        }
    }

    public boolean confirm(String prompt) {
        while (true) {
            String value = read(prompt);
            if (value.equalsIgnoreCase("y")) return true;
            if (value.equalsIgnoreCase("n")) return false;
            out.println("请输入 y 或 n。");
        }
    }
}

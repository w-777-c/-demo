package com.itheima.storage;

import com.itheima.doman.User;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class UserStore {
    private final Path file;
    private final Map<String, User> users = new LinkedHashMap<>();

    public UserStore(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (Files.exists(file)) load();
    }

    public static boolean validUsername(String value) {
        return value != null && value.matches("(?=.*[A-Za-z])[A-Za-z0-9]{3,16}");
    }

    public static boolean validPassword(String value) {
        return value != null && value.matches("(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{6,64}");
    }

    public User find(String username) { return users.get(username); }
    public Collection<User> all() { return new ArrayList<>(users.values()); }

    public User register(String username, String password) throws IOException {
        if (!validUsername(username) || !validPassword(password)) throw new IllegalArgumentException("Invalid credentials");
        if (users.containsKey(username)) throw new IllegalArgumentException("Username already exists");
        User user = new User(username, Passwords.hash(password), 0, 0, 0, 0);
        users.put(username, user);
        try {
            save();
        } catch (IOException exception) {
            users.remove(username);
            throw exception;
        }
        return user;
    }

    private void load() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            if (!"1".equals(properties.getProperty("version"))) throw new IllegalArgumentException("Unsupported version");
            int count = Integer.parseInt(required(properties, "count"));
            if (count < 0 || count > 100000) throw new IllegalArgumentException("Invalid user count");
            for (int i = 0; i < count; i++) {
                String prefix = "user." + i + ".";
                String name = required(properties, prefix + "name");
                String hash = required(properties, prefix + "hash");
                if (!validUsername(name) || !Passwords.isValidHash(hash) || users.containsKey(name)) {
                    throw new IllegalArgumentException("Invalid account record");
                }
                User user = new User(name, hash,
                        Integer.parseInt(required(properties, prefix + "games")),
                        Integer.parseInt(required(properties, prefix + "wins")),
                        Integer.parseInt(required(properties, prefix + "best")),
                        Integer.parseInt(required(properties, prefix + "clears")));
                users.put(name, user);
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("账号文件损坏或版本不兼容，原文件已保留：" + file, exception);
        }
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing field: " + key);
        return value;
    }

    public void save() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("count", Integer.toString(users.size()));
        int index = 0;
        for (User user : users.values()) {
            String prefix = "user." + index++ + ".";
            properties.setProperty(prefix + "name", user.getUsername());
            properties.setProperty(prefix + "hash", user.getPasswordHash());
            properties.setProperty(prefix + "games", Integer.toString(user.getGames()));
            properties.setProperty(prefix + "wins", Integer.toString(user.getTotalWins()));
            properties.setProperty(prefix + "best", Integer.toString(user.getBestWins()));
            properties.setProperty(prefix + "clears", Integer.toString(user.getClears()));
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "accounts-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Fighting game accounts - format 1");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

package com.itheima.doman;

public final class User {
    private final String username;
    private final String passwordHash;
    private int games;
    private int totalWins;
    private int bestWins;
    private int clears;

    public User(String username, String passwordHash, int games, int totalWins, int bestWins, int clears) {
        if (games < 0 || totalWins < 0 || bestWins < 0 || clears < 0 || bestWins > totalWins || clears > games) {
            throw new IllegalArgumentException("Invalid player statistics");
        }
        this.username = username;
        this.passwordHash = passwordHash;
        this.games = games;
        this.totalWins = totalWins;
        this.bestWins = bestWins;
        this.clears = clears;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public int getGames() { return games; }
    public int getTotalWins() { return totalWins; }
    public int getBestWins() { return bestWins; }
    public int getClears() { return clears; }

    public void recordGame(int wins, boolean cleared) {
        if (wins < 0 || (cleared && wins != 10)) throw new IllegalArgumentException("Invalid game result");
        games++;
        totalWins += wins;
        bestWins = Math.max(bestWins, wins);
        if (cleared) clears++;
    }
}

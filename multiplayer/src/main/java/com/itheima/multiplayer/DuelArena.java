package com.itheima.multiplayer;

import com.itheima.doman.HeroCharacter;
import com.itheima.game.Battle.Action;
import com.itheima.game.CombatRules;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Owned by DuelHandler's lock. Snapshots contain no tokens or mutable game objects. */
final class DuelArena {
    enum Phase { WAITING, FIGHTING, PAUSED, FINISHED }
    static final class RuleViolation extends RuntimeException {
        final String code;
        RuleViolation(String code) { super(code); this.code = code; }
    }
    record Seat(int seat, String token, String replacedConnection) {}
    record Skill(String action, boolean available, String reason) {}
    record Fighter(int seat, String name, boolean connected, long reconnectUntil, boolean ready, boolean rematch,
                   int hp, int maxHp, int attack, int defense, int potions, int cooldown, boolean guarding, List<Skill> skills) {}
    record Entry(long id, int turn, String text) {}
    record Snapshot(String matchId, long revision, Phase phase, int turn, int activeSeat, int winner,
                    String reason, List<Fighter> fighters, List<Entry> log, long serverTime) {}
    private static final class Player {
        final String token = UUID.randomUUID().toString();
        final String name;
        String connection;
        HeroCharacter hero;
        long deadline;
        int cooldown;
        boolean ready, rematch;
        Player(String name, String connection) { this.name = name; this.connection = connection; reset(); }
        void reset() { hero = HeroCharacter.create(name, 8, 10, 2); cooldown = 0; ready = rematch = false; }
    }
    private final Clock clock;
    private final long graceMillis;
    private final Player[] players = new Player[2];
    private final List<Entry> log = new ArrayList<>();
    private String matchId = UUID.randomUUID().toString();
    private long revision, entryId;
    private int turn = 1, activeSeat, starter, winner = -1;
    private boolean started, finished;
    private String reason = "";

    DuelArena(Clock clock, long graceMillis) { this.clock = clock; this.graceMillis = graceMillis; }
    long revision() { return revision; }
    int seatOf(String connection) {
        for (int i = 0; i < 2; i++) if (players[i] != null && connection.equals(players[i].connection)) return i;
        return -1;
    }
    private Player player(String connection) {
        int seat = seatOf(connection);
        require(seat >= 0, "NOT_SEATED");
        return players[seat];
    }
    private Phase phase() {
        if (finished) return Phase.FINISHED;
        if (!started) return Phase.WAITING;
        return players[0].connection == null || players[1].connection == null ? Phase.PAUSED : Phase.FIGHTING;
    }
    Seat join(String connection, String name) {
        require(seatOf(connection) < 0, "ALREADY_SEATED");
        require(name != null && name.matches("[\\p{L}\\p{N}_ -]{1,16}") && !name.isBlank(), "BAD_NAME");
        int seat = players[0] == null ? 0 : players[1] == null ? 1 : -1;
        require(seat >= 0, "ARENA_FULL");
        if (finished) reset(false);
        players[seat] = new Player(name.strip(), connection);
        add(players[seat].name + "加入了竞技场。");
        revision++;
        return new Seat(seat, players[seat].token, "");
    }
    Seat resume(String connection, String token) {
        for (int i = 0; i < 2; i++) {
            Player player = players[i];
            if (player == null || !player.token.equals(token)) continue;
            String previous = player.connection;
            player.connection = connection;
            player.deadline = 0;
            add(player.name + "已重新连接。");
            revision++;
            return new Seat(i, token, previous == null ? "" : previous);
        }
        throw new RuleViolation("SEAT_EXPIRED");
    }
    void ready(String connection) {
        require(phase() == Phase.WAITING, "NOT_WAITING");
        Player player = player(connection);
        require(!player.ready, "ALREADY_READY");
        player.ready = true;
        if (Arrays.stream(players).allMatch(p -> p != null && p.connection != null && p.ready)) {
            started = true;
            activeSeat = starter;
            add("对战开始，由" + players[activeSeat].name + "先手。");
        }
        revision++;
    }
    void action(String connection, long expectedTurn, Action action) {
        require(phase() == Phase.FIGHTING, "NOT_FIGHTING");
        int seat = seatOf(connection);
        require(seat >= 0, "NOT_SEATED");
        require(expectedTurn == turn, "STALE_TURN");
        require(seat == activeSeat, "NOT_YOUR_TURN");
        Player actor = players[seat], target = players[1 - seat];
        CombatRules.Result result = CombatRules.play(actor.hero, target.hero, action, actor.cooldown);
        require(result.accepted(), "SKILL_UNAVAILABLE");
        actor.cooldown = result.drainCooldown();
        for (String message : result.messages()) add(message);
        if (!target.hero.isAlive()) finish(seat, "击败对手");
        else if (turn >= 200) finish(-1, "达到 200 回合，平局");
        else { turn++; activeSeat = 1 - seat; }
        revision++;
    }
    void resign(String connection) {
        require(started && !finished, "NOT_FIGHTING");
        int seat = seatOf(connection);
        require(seat >= 0, "NOT_SEATED");
        finish(1 - seat, "对手认输");
        revision++;
    }
    void rematch(String connection) {
        require(finished && Arrays.stream(players).allMatch(p -> p != null && p.connection != null), "REMATCH_UNAVAILABLE");
        Player player = player(connection);
        require(!player.rematch, "ALREADY_READY");
        player.rematch = true;
        if (players[0].rematch && players[1].rematch) {
            starter = 1 - starter;
            reset(true);
        }
        revision++;
    }
    void leave(String connection) {
        int seat = seatOf(connection);
        require(seat >= 0, "NOT_SEATED");
        if (started && !finished) finish(1 - seat, "对手离开");
        add(players[seat].name + "离开了竞技场。");
        players[seat] = null;
        if (players[0] == null && players[1] == null) { starter = 0; reset(false); }
        revision++;
    }
    void disconnect(String connection) {
        int seat = seatOf(connection);
        if (seat < 0) return;
        Player player = players[seat];
        player.connection = null;
        player.deadline = clock.millis() + graceMillis;
        if (!started) player.ready = false;
        if (finished) player.rematch = false;
        add(player.name + "断开连接，席位暂时保留。");
        revision++;
    }
    void expire() {
        boolean[] expired = new boolean[2];
        for (int i = 0; i < 2; i++) expired[i] = players[i] != null && players[i].connection == null && players[i].deadline <= clock.millis();
        if (!expired[0] && !expired[1]) return;
        if (started && !finished) finish(expired[0] && expired[1] ? -1 : expired[0] ? 1 : 0, "断线重连超时");
        for (int i = 0; i < 2; i++) if (expired[i]) { add(players[i].name + "的席位已释放。"); players[i] = null; }
        if (players[0] == null && players[1] == null) { starter = 0; reset(false); }
        revision++;
    }
    void checkMatch(String expected) { require(matchId.equals(expected), "STALE_MATCH"); }
    private void finish(int winner, String reason) {
        finished = true;
        this.winner = winner;
        this.reason = reason;
        add(winner < 0 ? "本局平局。" : players[winner].name + "获胜（" + reason + "）。");
    }
    private void reset(boolean immediatelyStart) {
        matchId = UUID.randomUUID().toString();
        started = immediatelyStart;
        finished = false;
        winner = -1;
        reason = "";
        turn = 1;
        activeSeat = starter;
        log.clear();
        for (Player player : players) if (player != null) { player.reset(); player.ready = immediatelyStart; }
        if (immediatelyStart) add("新一局开始，由" + players[activeSeat].name + "先手。");
    }
    private void add(String text) {
        log.add(new Entry(++entryId, turn, text));
        if (log.size() > 80) log.remove(0);
    }
    Snapshot snapshot() {
        List<Fighter> fighters = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Player p = players[i];
            if (p == null) continue;
            List<Skill> skills = Arrays.stream(Action.values()).map(action -> {
                String reason = CombatRules.unavailable(p.hero, action, p.cooldown);
                return new Skill(action.name(), reason.isEmpty(), reason);
            }).toList();
            fighters.add(new Fighter(i, p.name, p.connection != null, p.deadline, p.ready, p.rematch,
                    p.hero.getHP(), p.hero.getMaxHP(), p.hero.getAttack(), p.hero.getDefense(), p.hero.getPotions(), p.cooldown, p.hero.isDefending(), skills));
        }
        return new Snapshot(matchId, revision, phase(), turn, activeSeat, winner, reason, List.copyOf(fighters), List.copyOf(log), clock.millis());
    }
    private static void require(boolean condition, String code) { if (!condition) throw new RuleViolation(code); }
}

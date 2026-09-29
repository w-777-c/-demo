package com.itheima.multiplayer;

import static org.junit.jupiter.api.Assertions.*;

import com.itheima.game.Battle.Action;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 竞技场状态机单元测试：不经过网络，直接验证回合、断线、重赛和结算规则。 */
class DuelArenaTest {
    private final TestClock clock = new TestClock();
    private DuelArena arena;
    @BeforeEach void create() {
        // 使用可控时钟，让断线宽限期和超时分支可以确定性复现。
        arena = new DuelArena(clock, 30000);
    }
    private void start() { arena.join("a", "青锋"); arena.join("b", "赤刃"); arena.ready("a"); arena.ready("b"); }
    private DuelArena.Fighter fighter(int seat) { return arena.snapshot().fighters().stream().filter(p -> p.seat() == seat).findFirst().orElseThrow(); }
    private void play(String connection, Action action) { arena.action(connection, arena.snapshot().turn(), action); }
    private void rejected(String code, Runnable operation) {
        long revision = arena.revision();
        assertEquals(code, assertThrows(DuelArena.RuleViolation.class, operation::run).code);
        assertEquals(revision, arena.revision(), "Rejected operation must not change state");
    }

    @Test void requiresBothPlayersReadyAndRejectsUnauthorizedOrStaleActions() {
        arena.join("a", "青锋"); arena.ready("a");
        assertEquals(DuelArena.Phase.WAITING, arena.snapshot().phase());
        arena.join("b", "赤刃"); arena.ready("b");
        assertEquals(DuelArena.Phase.FIGHTING, arena.snapshot().phase());
        rejected("ARENA_FULL", () -> arena.join("c", "第三人"));
        rejected("NOT_SEATED", () -> play("c", Action.ATTACK));
        rejected("NOT_YOUR_TURN", () -> play("b", Action.ATTACK));
        rejected("SKILL_UNAVAILABLE", () -> play("a", Action.POTION));
        play("a", Action.ATTACK);
        assertEquals(152, fighter(1).hp());
        assertEquals(180, fighter(0).hp());
        rejected("STALE_TURN", () -> arena.action("b", 1, Action.ATTACK));
        play("b", Action.DEFEND);
        play("a", Action.ATTACK);
        assertEquals(138, fighter(1).hp());
        assertFalse(fighter(1).guarding());
    }

    @Test void reusesSkillCostsCooldownsAndPotionLimits() {
        start();
        play("a", Action.DRAIN);
        assertEquals(146, fighter(1).hp());
        assertEquals(2, fighter(0).cooldown());
        play("b", Action.ATTACK);
        rejected("SKILL_UNAVAILABLE", () -> play("a", Action.DRAIN));
        play("a", Action.POTION);
        assertEquals(180, fighter(0).hp());
        assertEquals(2, fighter(0).potions());
        play("b", Action.DEFEND);
        play("a", Action.POWER_STRIKE);
        assertEquals(170, fighter(0).hp());
        assertEquals(120, fighter(1).hp());
        assertEquals(0, fighter(0).cooldown());
    }

    @Test void lethalAttackFinishesOnceAndRematchRequiresBothConsents() {
        start();
        String original = arena.snapshot().matchId();
        while (arena.snapshot().phase() == DuelArena.Phase.FIGHTING) play(arena.snapshot().activeSeat() == 0 ? "a" : "b", Action.ATTACK);
        assertEquals(0, arena.snapshot().winner());
        assertEquals(0, fighter(1).hp());
        rejected("NOT_FIGHTING", () -> play("b", Action.ATTACK));
        arena.rematch("a");
        assertEquals(original, arena.snapshot().matchId());
        arena.rematch("b");
        assertNotEquals(original, arena.snapshot().matchId());
        assertEquals(1, arena.snapshot().activeSeat());
        assertEquals(180, fighter(0).hp());
        assertEquals(3, fighter(1).potions());
        rejected("STALE_MATCH", () -> arena.checkMatch(original));
    }

    @Test void disconnectPausesAndTokenRestoresExactlyTheSameTurnAndHealth() {
        DuelArena.Seat a = arena.join("a", "青锋"); arena.join("b", "赤刃"); arena.ready("a"); arena.ready("b");
        play("a", Action.ATTACK);
        arena.disconnect("a");
        assertEquals(DuelArena.Phase.PAUSED, arena.snapshot().phase());
        rejected("NOT_FIGHTING", () -> play("b", Action.ATTACK));
        rejected("SEAT_EXPIRED", () -> arena.resume("intruder", "wrong-token"));
        clock.advance(29000); arena.expire();
        assertEquals(0, arena.resume("new-a", a.token()).seat());
        assertEquals(DuelArena.Phase.FIGHTING, arena.snapshot().phase());
        assertEquals(2, arena.snapshot().turn());
        assertEquals(152, fighter(1).hp());
        assertEquals(-1, arena.seatOf("a"));
        play("b", Action.ATTACK);
    }

    @Test void expiryAwardsTheOtherPlayerAndReleasesTheSeat() {
        start();
        arena.disconnect("a");
        clock.advance(30000); arena.expire();
        assertEquals(DuelArena.Phase.FINISHED, arena.snapshot().phase());
        assertEquals(1, arena.snapshot().winner());
        assertEquals(1, arena.snapshot().fighters().size());
        arena.join("c", "新对手");
        assertEquals(DuelArena.Phase.WAITING, arena.snapshot().phase());
        assertEquals(180, fighter(1).hp());
        assertFalse(fighter(1).ready());
    }

    @Test void simultaneousExpiryAndLeavingDoNotStrandTheArena() {
        start(); arena.disconnect("a"); arena.disconnect("b"); clock.advance(30000); arena.expire();
        assertEquals(DuelArena.Phase.WAITING, arena.snapshot().phase());
        assertTrue(arena.snapshot().fighters().isEmpty());
        start(); arena.leave("a");
        assertEquals(1, arena.snapshot().winner());
        arena.leave("b");
        assertTrue(arena.snapshot().fighters().isEmpty());
        assertEquals(DuelArena.Phase.WAITING, arena.snapshot().phase());
    }

    @Test void reconnectTokenCanReplaceAStaleSocketWithoutGivingItControl() {
        DuelArena.Seat a = arena.join("a", "青锋"); arena.join("b", "赤刃"); arena.ready("a"); arena.ready("b");
        assertEquals("a", arena.resume("new-a", a.token()).replacedConnection());
        arena.disconnect("a");
        assertEquals(DuelArena.Phase.FIGHTING, arena.snapshot().phase());
        rejected("NOT_SEATED", () -> play("a", Action.ATTACK));
        play("new-a", Action.ATTACK);
    }

    @Test void resignationAndTurnLimitProduceServerOwnedOutcomes() {
        start(); arena.resign("b");
        assertEquals(0, arena.snapshot().winner());
        arena.rematch("a"); arena.rematch("b");
        for (int i = 0; i < 200; i++) play(arena.snapshot().activeSeat() == 0 ? "a" : "b", Action.DEFEND);
        assertEquals(DuelArena.Phase.FINISHED, arena.snapshot().phase());
        assertEquals(-1, arena.snapshot().winner());
        assertTrue(arena.snapshot().log().size() <= 80);
    }

    private static final class TestClock extends Clock {
        long now = 1000000;
        void advance(long millis) { now += millis; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }
}

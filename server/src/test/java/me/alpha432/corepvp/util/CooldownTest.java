package me.alpha432.corepvp.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTest {

    private final AtomicLong clock = new AtomicLong(0L);
    private final Cooldown cooldown = new Cooldown(clock::get);
    private final UUID player = UUID.randomUUID();

    @Test
    void firstUseIsAllowed() {
        assertTrue(cooldown.tryUse(player, 1_000L));
    }

    @Test
    void secondUseWithinTheWindowIsRejected() {
        assertTrue(cooldown.tryUse(player, 1_000L));
        clock.set(999L);
        assertFalse(cooldown.tryUse(player, 1_000L));
    }

    @Test
    void useIsAllowedAgainExactlyWhenTheWindowElapses() {
        assertTrue(cooldown.tryUse(player, 1_000L));
        clock.set(1_000L);
        assertTrue(cooldown.tryUse(player, 1_000L));
    }

    @Test
    void rejectedUseDoesNotExtendTheWindow() {
        assertTrue(cooldown.tryUse(player, 1_000L));
        clock.set(500L);
        assertFalse(cooldown.tryUse(player, 1_000L));
        clock.set(1_000L);
        assertTrue(cooldown.tryUse(player, 1_000L), "a blocked attempt must not push the deadline back");
    }

    @Test
    void remainingCountsDown() {
        cooldown.set(player, 1_000L);
        assertEquals(1_000L, cooldown.remainingMillis(player));
        clock.set(400L);
        assertEquals(600L, cooldown.remainingMillis(player));
        clock.set(5_000L);
        assertEquals(0L, cooldown.remainingMillis(player), "remaining never goes negative");
    }

    @Test
    void unknownPlayersAreReady() {
        assertTrue(cooldown.isReady(UUID.randomUUID()));
        assertEquals(0L, cooldown.remainingMillis(UUID.randomUUID()));
    }

    @Test
    void resetClearsTheWindow() {
        cooldown.set(player, 10_000L);
        assertFalse(cooldown.isReady(player));
        cooldown.reset(player);
        assertTrue(cooldown.isReady(player));
    }
}

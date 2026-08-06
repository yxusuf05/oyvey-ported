package me.alpha432.network.core.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownsTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void unknownPlayerHasNoCooldown() {
        Cooldowns cooldowns = new Cooldowns();

        assertFalse(cooldowns.isActive(player));
        assertEquals(0L, cooldowns.remainingMillis(player));
    }

    @Test
    void activeUntilItExpires() {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, 5_000L);

        assertTrue(cooldowns.isActive(player));
        assertTrue(cooldowns.remainingMillis(player) > 4_000L);
        assertEquals(5L, cooldowns.remainingSeconds(player));
    }

    @Test
    void zeroOrNegativeClearsTheCooldown() {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, 5_000L);
        cooldowns.set(player, 0L);

        assertFalse(cooldowns.isActive(player));
    }

    @Test
    void expiredCooldownIsDropped() throws InterruptedException {
        Cooldowns cooldowns = new Cooldowns();
        cooldowns.set(player, 20L);
        Thread.sleep(40L);

        assertFalse(cooldowns.isActive(player));
        assertEquals(0L, cooldowns.remainingMillis(player));
    }

    @Test
    void clearRemovesASingleEntry() {
        Cooldowns cooldowns = new Cooldowns();
        UUID other = UUID.randomUUID();
        cooldowns.setSeconds(player, 10);
        cooldowns.setSeconds(other, 10);

        cooldowns.clear(player);

        assertFalse(cooldowns.isActive(player));
        assertTrue(cooldowns.isActive(other));
    }
}

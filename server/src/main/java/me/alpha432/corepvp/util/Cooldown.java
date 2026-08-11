package me.alpha432.corepvp.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-player cooldown tracker.
 *
 * <p>The clock is injectable so the behaviour can be tested without sleeping.
 */
public final class Cooldown {

    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public Cooldown() {
        this(System::currentTimeMillis);
    }

    public Cooldown(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Consumes the cooldown: returns true and starts a new one when the player
     * is ready, false when they must still wait.
     */
    public boolean tryUse(UUID uuid, long durationMillis) {
        long now = clock.getAsLong();
        Long ready = readyAt.get(uuid);
        if (ready != null && ready > now) {
            return false;
        }
        readyAt.put(uuid, now + durationMillis);
        return true;
    }

    public boolean isReady(UUID uuid) {
        Long ready = readyAt.get(uuid);
        return ready == null || ready <= clock.getAsLong();
    }

    public long remainingMillis(UUID uuid) {
        Long ready = readyAt.get(uuid);
        if (ready == null) {
            return 0L;
        }
        return Math.max(0L, ready - clock.getAsLong());
    }

    public void set(UUID uuid, long durationMillis) {
        readyAt.put(uuid, clock.getAsLong() + durationMillis);
    }

    public void reset(UUID uuid) {
        readyAt.remove(uuid);
    }

    public void clear() {
        readyAt.clear();
    }
}

package me.alpha432.network.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks per-player cooldowns for a single feature. */
public final class Cooldowns {

    private final Map<UUID, Long> expiry = new HashMap<>();

    public boolean isActive(UUID id) {
        Long until = expiry.get(id);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            expiry.remove(id);
            return false;
        }
        return true;
    }

    public long remainingMillis(UUID id) {
        Long until = expiry.get(id);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    public long remainingSeconds(UUID id) {
        return (remainingMillis(id) + 999L) / 1000L;
    }

    public void set(UUID id, long millis) {
        if (millis <= 0) {
            expiry.remove(id);
        } else {
            expiry.put(id, System.currentTimeMillis() + millis);
        }
    }

    public void setSeconds(UUID id, long seconds) {
        set(id, seconds * 1000L);
    }

    public void clear(UUID id) {
        expiry.remove(id);
    }

    public void clearAll() {
        expiry.clear();
    }
}

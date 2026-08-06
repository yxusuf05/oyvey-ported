package me.alpha432.network.staff.punishment;

import java.util.UUID;

/**
 * One entry of the punishment log.
 *
 * @param expiresAt 0 means permanent
 * @param active    false once the punishment was lifted
 */
public record Punishment(String id,
                         UUID target,
                         String targetName,
                         PunishmentType type,
                         String reason,
                         String actor,
                         long createdAt,
                         long expiresAt,
                         boolean active) {

    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }

    /** Active, not lifted and not run out. */
    public boolean isInEffect() {
        return active && !isExpired();
    }

    public long remainingMillis() {
        return isPermanent() ? Long.MAX_VALUE : Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public static Punishment create(UUID target, String targetName, PunishmentType type,
                                    String reason, String actor, long durationMillis) {
        long now = System.currentTimeMillis();
        return new Punishment(UUID.randomUUID().toString(), target, targetName, type, reason, actor,
                now, durationMillis <= 0 ? 0L : now + durationMillis, true);
    }
}

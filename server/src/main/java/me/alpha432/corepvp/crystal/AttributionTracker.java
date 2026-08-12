package me.alpha432.corepvp.crystal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Remembers who is responsible for something that later deals damage.
 *
 * <p>Crystal and anchor explosions arrive with no attacker attached, so a kill
 * would otherwise be credited to nobody. Entries expire on their own, which
 * both keeps the maps small and stops a crystal placed five minutes ago from
 * being blamed for a kill.
 *
 * <p>Free of Bukkit types so the expiry and nearest-match rules can be tested.
 */
public final class AttributionTracker {

    /** A detonation at a point in space, used for block explosions. */
    private record Positioned(int x, int y, int z, UUID owner, long at) {

        double distanceSquared(int px, int py, int pz) {
            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record Owned(UUID owner, long at) {
    }

    private final LongSupplier clock;
    private final long entityTtlMillis;
    private final long positionTtlMillis;

    private final Map<UUID, Owned> byEntity = new HashMap<>();
    private final List<Positioned> byPosition = new ArrayList<>();

    public AttributionTracker(long entityTtlMillis, long positionTtlMillis) {
        this(entityTtlMillis, positionTtlMillis, System::currentTimeMillis);
    }

    public AttributionTracker(long entityTtlMillis, long positionTtlMillis, LongSupplier clock) {
        this.entityTtlMillis = entityTtlMillis;
        this.positionTtlMillis = positionTtlMillis;
        this.clock = clock;
    }

    /** Records that {@code owner} placed the entity with this id. */
    public void rememberEntity(UUID entityId, UUID owner) {
        byEntity.put(entityId, new Owned(owner, clock.getAsLong()));
    }

    public UUID ownerOfEntity(UUID entityId) {
        Owned owned = byEntity.get(entityId);
        if (owned == null) {
            return null;
        }
        if (clock.getAsLong() - owned.at() > entityTtlMillis) {
            byEntity.remove(entityId);
            return null;
        }
        return owned.owner();
    }

    public void forgetEntity(UUID entityId) {
        byEntity.remove(entityId);
    }

    /** Records that {@code owner} set something off at these coordinates. */
    public void rememberPosition(int x, int y, int z, UUID owner) {
        byPosition.add(new Positioned(x, y, z, owner, clock.getAsLong()));
    }

    /**
     * The most recent nearby detonation, or null.
     *
     * <p>Nearest wins, so two anchors going off at once still credit the right
     * player for each blast.
     */
    public UUID ownerNear(int x, int y, int z, double radius) {
        long now = clock.getAsLong();
        double radiusSquared = radius * radius;

        UUID best = null;
        double bestDistance = Double.MAX_VALUE;
        long bestAt = Long.MIN_VALUE;

        Iterator<Positioned> iterator = byPosition.iterator();
        while (iterator.hasNext()) {
            Positioned entry = iterator.next();
            if (now - entry.at() > positionTtlMillis) {
                iterator.remove();
                continue;
            }
            double distance = entry.distanceSquared(x, y, z);
            if (distance > radiusSquared) {
                continue;
            }
            if (distance < bestDistance || (distance == bestDistance && entry.at() > bestAt)) {
                best = entry.owner();
                bestDistance = distance;
                bestAt = entry.at();
            }
        }
        return best;
    }

    /** Drops expired entries. Called on a timer so idle maps do not grow. */
    public void sweep() {
        long now = clock.getAsLong();
        byEntity.entrySet().removeIf(entry -> now - entry.getValue().at() > entityTtlMillis);
        byPosition.removeIf(entry -> now - entry.at() > positionTtlMillis);
    }

    public int trackedEntities() {
        return byEntity.size();
    }

    public int trackedPositions() {
        return byPosition.size();
    }

    public void clear() {
        byEntity.clear();
        byPosition.clear();
    }
}

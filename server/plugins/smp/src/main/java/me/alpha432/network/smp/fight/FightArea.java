package me.alpha432.network.smp.fight;

import me.alpha432.network.core.region.Region;
import me.alpha432.network.core.util.BlockRestore;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Set;

/**
 * A PvP area at the SMP spawn.
 *
 * <p>A {@link Type#SWORD} area is a pure melee ring: only the allowed weapons work and nothing
 * can be built. A {@link Type#CRYSTAL} area allows the usual crystal setup and is rolled back
 * on a timer, so the ground is clean again for the next fight.
 */
public final class FightArea {

    /** What kind of fighting the area is for. */
    public enum Type {
        /** Melee only, no building at all. */
        SWORD,
        /** Crystal PvP, building allowed inside and reset on a timer. */
        CRYSTAL
    }

    private final String name;
    private final Type type;
    private final Region region;
    private final Set<Material> allowedItems;
    private final int resetSeconds;
    private final BlockRestore changes = new BlockRestore();
    private Location spawn;
    private long lastReset = System.currentTimeMillis();

    public FightArea(String name, Type type, Region region, Set<Material> allowedItems,
                     int resetSeconds, Location spawn) {
        this.name = name;
        this.type = type;
        this.region = region;
        this.allowedItems = allowedItems;
        this.resetSeconds = resetSeconds;
        this.spawn = spawn;
    }

    public boolean contains(Location location) {
        return region != null && region.contains(location);
    }

    /**
     * Whether an item may be used in this area. Only sword areas restrict items; an empty
     * allow list means everything goes.
     */
    public boolean allows(Material material) {
        if (type != Type.SWORD || allowedItems.isEmpty()) {
            return true;
        }
        return allowedItems.contains(material);
    }

    public boolean allowsBuilding() {
        return type == Type.CRYSTAL;
    }

    /** Seconds until the next automatic reset; 0 when resets are switched off. */
    public long secondsUntilReset() {
        if (resetSeconds <= 0) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - lastReset) / 1000L;
        return Math.max(0L, resetSeconds - elapsed);
    }

    public boolean isResetDue() {
        return resetSeconds > 0 && secondsUntilReset() == 0;
    }

    /** Restores every recorded block and starts the next interval. */
    public int reset() {
        lastReset = System.currentTimeMillis();
        return changes.restore();
    }

    public boolean hasChanges() {
        return changes.size() > 0;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public Region region() {
        return region;
    }

    public Set<Material> allowedItems() {
        return allowedItems;
    }

    public int resetSeconds() {
        return resetSeconds;
    }

    public BlockRestore changes() {
        return changes;
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    public void spawn(Location spawn) {
        this.spawn = spawn == null ? null : spawn.clone();
    }
}

package me.alpha432.corepvp.ffa;

import me.alpha432.corepvp.arena.Cuboid;
import org.bukkit.Location;

import java.util.Locale;

/**
 * A permanent free-for-all arena.
 *
 * @param safeRadius how far from spawn players cannot be hurt, so nobody is
 *                   killed the instant they respawn
 */
public record FfaArena(String id, String kitId, Location spawn, double safeRadius, Cuboid bounds, int deathY) {

    public FfaArena {
        id = id.toLowerCase(Locale.ROOT);
        kitId = kitId.toLowerCase(Locale.ROOT);
    }

    public boolean inSafeZone(Location location) {
        return location != null
                && location.getWorld() != null
                && spawn.getWorld() != null
                && location.getWorld().equals(spawn.getWorld())
                && location.distanceSquared(spawn) <= safeRadius * safeRadius;
    }

    public boolean contains(Location location) {
        return bounds == null || bounds.contains(location);
    }
}

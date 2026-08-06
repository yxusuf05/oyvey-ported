package me.alpha432.network.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Converts locations to and from the flat string form used in YAML and the database. */
public final class Locations {

    private Locations() {
    }

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return String.join(";",
                location.getWorld().getName(),
                Double.toString(location.getX()),
                Double.toString(location.getY()),
                Double.toString(location.getZ()),
                Float.toString(location.getYaw()),
                Float.toString(location.getPitch()));
    }

    /** Returns {@code null} when the string is malformed or the world is not loaded. */
    public static Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String pretty(Location location) {
        if (location == null) {
            return "-";
        }
        return String.format("%s %d, %d, %d",
                location.getWorld() == null ? "?" : location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static Location center(Location location) {
        return new Location(location.getWorld(),
                location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5,
                location.getYaw(), location.getPitch());
    }

    /** A spot is safe when the player fits into two non-solid blocks and does not stand in lava. */
    public static boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }
        if (feet.isLiquid() && feet.getType().name().contains("LAVA")) {
            return false;
        }
        return ground.getType().isSolid();
    }

    /**
     * Looks for a safe spot at or near {@code origin}: first straight down, then straight up.
     * Falls back to the untouched origin when nothing is found (void worlds, for example).
     */
    public static Location findSafe(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return origin;
        }
        if (isSafe(origin)) {
            return origin;
        }
        World world = origin.getWorld();
        int startY = origin.getBlockY();
        for (int y = startY; y >= world.getMinHeight() + 1; y--) {
            Location candidate = withY(origin, y);
            if (isSafe(candidate)) {
                return candidate;
            }
        }
        for (int y = startY; y < world.getMaxHeight() - 2; y++) {
            Location candidate = withY(origin, y);
            if (isSafe(candidate)) {
                return candidate;
            }
        }
        return origin;
    }

    private static Location withY(Location origin, int y) {
        return new Location(origin.getWorld(), origin.getX(), y, origin.getZ(),
                origin.getYaw(), origin.getPitch());
    }

    /** True when both locations point at the same block in the same world. */
    public static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}

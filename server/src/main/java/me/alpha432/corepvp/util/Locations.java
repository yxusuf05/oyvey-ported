package me.alpha432.corepvp.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Compact {@code world,x,y,z,yaw,pitch} form for storing locations in YAML. */
public final class Locations {

    private Locations() {
    }

    public static String serialize(Location location) {
        return location.getWorld().getName()
                + "," + round(location.getX())
                + "," + round(location.getY())
                + "," + round(location.getZ())
                + "," + round(location.getYaw())
                + "," + round(location.getPitch());
    }

    /** Returns null when the string is malformed or the world is not loaded. */
    public static Location deserialize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] parts = input.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0.0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0.0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Centres on the block in X/Z, which is where you want a spawn point. */
    public static Location centered(Location location) {
        Location copy = location.clone();
        copy.setX(location.getBlockX() + 0.5D);
        copy.setZ(location.getBlockZ() + 0.5D);
        return copy;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static double round(float value) {
        return Math.round(value * 100.0F) / 100.0D;
    }
}

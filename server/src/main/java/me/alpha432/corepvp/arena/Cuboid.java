package me.alpha432.corepvp.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** An axis-aligned box, inclusive on both corners. */
public final class Cuboid {

    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid between(Location a, Location b) {
        return new Cuboid(a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public String world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Cuboid expand(int horizontal, int vertical) {
        return new Cuboid(world,
                minX - horizontal, minY - vertical, minZ - horizontal,
                maxX + horizontal, maxY + vertical, maxZ + horizontal);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public Location center() {
        World bukkitWorld = Bukkit.getWorld(world);
        return new Location(bukkitWorld,
                (minX + maxX) / 2.0D + 0.5D,
                (minY + maxY) / 2.0D,
                (minZ + maxZ) / 2.0D + 0.5D);
    }

    public String serialize() {
        return world + "," + minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }

    public static Cuboid deserialize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] parts = input.split(",");
        if (parts.length < 7) {
            return null;
        }
        try {
            return new Cuboid(parts[0],
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Cuboid[" + serialize() + "]";
    }
}

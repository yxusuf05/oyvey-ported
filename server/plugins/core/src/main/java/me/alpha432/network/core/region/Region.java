package me.alpha432.network.core.region;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.Map;

/** An axis aligned cuboid with a set of flags; used for spawn and lobby protection. */
public final class Region {

    private final String name;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Map<RegionFlag, Boolean> flags = new EnumMap<>(RegionFlag.class);
    private int priority;

    public Region(String name, String world,
                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static Region between(String name, Location a, Location b) {
        World world = a.getWorld();
        return new Region(name, world == null ? "world" : world.getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    /** Creates a square region around a centre point spanning the full world height. */
    public static Region around(String name, Location center, int radius) {
        World world = center.getWorld();
        int minY = world == null ? -64 : world.getMinHeight();
        int maxY = world == null ? 320 : world.getMaxHeight();
        return new Region(name, world == null ? "world" : world.getName(),
                center.getBlockX() - radius, minY, center.getBlockZ() - radius,
                center.getBlockX() + radius, maxY, center.getBlockZ() + radius);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** {@code null} means the region does not care about this flag. */
    public Boolean flag(RegionFlag flag) {
        return flags.get(flag);
    }

    public Region flag(RegionFlag flag, boolean allowed) {
        flags.put(flag, allowed);
        return this;
    }

    public Region clearFlag(RegionFlag flag) {
        flags.remove(flag);
        return this;
    }

    public Map<RegionFlag, Boolean> flags() {
        return flags;
    }

    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public int priority() {
        return priority;
    }

    public Region priority(int priority) {
        this.priority = priority;
        return this;
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

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}

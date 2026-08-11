package me.alpha432.corepvp.arena.rollback;

import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.arena.Cuboid;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;

/** Puts an arena back the way it was, spread over several ticks. */
public final class RollbackService {

    private final Plugin plugin;
    private final int blocksPerTick;

    public RollbackService(Plugin plugin, int blocksPerTick) {
        this.plugin = plugin;
        this.blocksPerTick = Math.max(64, blocksPerTick);
    }

    /**
     * Restores an arena asynchronously over several ticks and runs
     * {@code onComplete} on the main thread once it is done.
     */
    public void restore(Arena arena, Runnable onComplete) {
        List<Map.Entry<Long, BlockData>> order = arena.journal().restoreOrder();
        clearEntities(arena);

        if (order.isEmpty()) {
            arena.journal().clear();
            Tasks.sync(onComplete);
            return;
        }

        World world = world(arena);
        if (world == null) {
            plugin.getLogger().warning("Cannot roll back arena " + arena.id() + ": world is not loaded");
            arena.journal().clear();
            Tasks.sync(onComplete);
            return;
        }

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int budget = blocksPerTick;
                while (index < order.size() && budget-- > 0) {
                    Map.Entry<Long, BlockData> entry = order.get(index++);
                    long key = entry.getKey();
                    Block block = world.getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key));
                    // Physics off: neighbouring updates would knock down blocks
                    // we are about to restore anyway, and cost a lot of ticks.
                    block.setBlockData(entry.getValue(), false);
                }
                if (index >= order.size()) {
                    cancel();
                    arena.journal().clear();
                    onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Restores everything immediately. Used on shutdown, where the scheduler is
     * gone and a task-based restore would silently never run - leaving the
     * arena full of last match's obsidian forever.
     */
    public void restoreBlocking(Arena arena) {
        World world = world(arena);
        if (world == null) {
            arena.journal().clear();
            return;
        }
        clearEntities(arena);
        for (Map.Entry<Long, BlockData> entry : arena.journal().restoreOrder()) {
            long key = entry.getKey();
            world.getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key))
                    .setBlockData(entry.getValue(), false);
        }
        arena.journal().clear();
    }

    /** Removes the leftovers a fight produces: dropped items, arrows, crystals, orbs. */
    public void clearEntities(Arena arena) {
        Cuboid bounds = arena.bounds();
        World world = world(arena);
        if (bounds == null || world == null) {
            return;
        }
        Location center = bounds.center();
        double radius = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) / 2.0D + 8.0D;
        double height = (bounds.maxY() - bounds.minY()) / 2.0D + 8.0D;

        for (Entity entity : world.getNearbyEntities(center, radius, height, radius)) {
            if (entity instanceof Item
                    || entity instanceof Projectile
                    || entity instanceof ExperienceOrb
                    || entity instanceof TNTPrimed
                    || entity instanceof FallingBlock
                    || entity instanceof EnderCrystal) {
                entity.remove();
            }
        }
    }

    private World world(Arena arena) {
        Cuboid bounds = arena.bounds();
        return bounds == null ? null : Bukkit.getWorld(bounds.world());
    }
}

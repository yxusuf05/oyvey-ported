package me.alpha432.corepvp.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Thin wrapper around the Bukkit scheduler so call sites read as
 * {@code Tasks.async(...)} instead of dragging the plugin instance around.
 */
public final class Tasks {

    private static Plugin plugin;

    private Tasks() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    private static Plugin plugin() {
        if (plugin == null) {
            throw new IllegalStateException("Tasks used before Tasks.init(plugin)");
        }
        return plugin;
    }

    /** Runs on the main thread, immediately if we are already on it. */
    public static void sync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin(), runnable);
        }
    }

    /** Always schedules onto the main thread on the next tick. */
    public static BukkitTask nextTick(Runnable runnable) {
        return Bukkit.getScheduler().runTask(plugin(), runnable);
    }

    public static BukkitTask later(Runnable runnable, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin(), runnable, delayTicks);
    }

    public static BukkitTask async(Runnable runnable) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin(), runnable);
    }

    public static BukkitTask timer(Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin(), runnable, delayTicks, periodTicks);
    }

    public static BukkitTask asyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin(), runnable, delayTicks, periodTicks);
    }
}

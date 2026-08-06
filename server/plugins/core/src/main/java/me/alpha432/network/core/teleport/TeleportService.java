package me.alpha432.network.core.teleport;

import me.alpha432.network.core.text.Messages;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Every teleport in the network goes through here so warmup, cancel-on-move and the
 * {@code /back} history behave the same everywhere.
 */
public final class TeleportService implements Listener {

    public static final String INSTANT_PERMISSION = "network.teleport.instant";

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, Deque<Location>> backHistory = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final int backHistorySize;

    public TeleportService(Plugin plugin, Messages messages, int backHistorySize) {
        this.plugin = plugin;
        this.messages = messages;
        this.backHistorySize = Math.max(1, backHistorySize);
    }

    /** Teleports right away and remembers where the player came from. */
    public CompletableFuture<Boolean> teleport(Player player, Location target) {
        return teleport(player, target, true);
    }

    public CompletableFuture<Boolean> teleport(Player player, Location target, boolean recordBack) {
        if (target == null || target.getWorld() == null) {
            messages.send(player, "teleport.invalid-target");
            return CompletableFuture.completedFuture(false);
        }
        if (recordBack) {
            pushBack(player, player.getLocation());
        }
        return player.teleportAsync(target);
    }

    public CompletableFuture<Boolean> teleportToWorldSpawn(Player player, World world) {
        return teleport(player, world.getSpawnLocation());
    }

    /**
     * Starts a countdown before teleporting. Moving to another block or taking damage aborts it.
     * Players with {@link #INSTANT_PERMISSION} skip the wait.
     */
    public void teleportWithWarmup(Player player, Location target, int seconds) {
        teleportWithWarmup(player, target, seconds, null);
    }

    public void teleportWithWarmup(Player player, Location target, int seconds, Runnable onComplete) {
        if (target == null || target.getWorld() == null) {
            messages.send(player, "teleport.invalid-target");
            return;
        }
        cancel(player, false);
        if (seconds <= 0 || player.hasPermission(INSTANT_PERMISSION)) {
            finish(player, target, onComplete);
            return;
        }

        messages.send(player, "teleport.warmup-start", "<seconds>", String.valueOf(seconds));
        Location origin = player.getLocation().clone();
        int[] remaining = {seconds};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            remaining[0]--;
            if (remaining[0] > 0) {
                messages.actionBar(player, "teleport.warmup-tick", "<seconds>", String.valueOf(remaining[0]));
                return;
            }
            Warmup warmup = warmups.remove(player.getUniqueId());
            if (warmup != null) {
                warmup.task().cancel();
            }
            finish(player, target, onComplete);
        }, 20L, 20L);
        warmups.put(player.getUniqueId(), new Warmup(origin, target, task, onComplete));
    }

    private void finish(Player player, Location target, Runnable onComplete) {
        teleport(player, Locations.findSafe(target)).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                messages.send(player, "teleport.done");
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    public boolean hasWarmup(Player player) {
        return warmups.containsKey(player.getUniqueId());
    }

    public void cancel(Player player, boolean notify) {
        Warmup warmup = warmups.remove(player.getUniqueId());
        if (warmup == null) {
            return;
        }
        warmup.task().cancel();
        if (notify) {
            messages.send(player, "teleport.cancelled");
        }
    }

    public void pushBack(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Deque<Location> history = backHistory.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        history.push(location.clone());
        while (history.size() > backHistorySize) {
            history.removeLast();
        }
    }

    public Optional<Location> popBack(Player player) {
        Deque<Location> history = backHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.pop());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup == null) {
            return;
        }
        if (!Locations.sameBlock(warmup.origin(), event.getTo())) {
            cancel(event.getPlayer(), false);
            messages.send(event.getPlayer(), "teleport.cancelled-move");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !hasWarmup(player)) {
            return;
        }
        cancel(player, false);
        messages.send(player, "teleport.cancelled-damage");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), false);
        backHistory.remove(event.getPlayer().getUniqueId());
    }

    private record Warmup(Location origin, Location target, BukkitTask task, Runnable onComplete) {
    }
}

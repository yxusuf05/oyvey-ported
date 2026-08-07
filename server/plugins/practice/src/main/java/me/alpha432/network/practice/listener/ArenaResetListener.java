package me.alpha432.network.practice.listener;

import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.match.Match;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.List;

/**
 * Records everything that changes a block inside a running match, so the arena is byte for byte
 * the same when the next duel starts there.
 *
 * <p>The player's own place and break events are handled in {@link PracticeListener}; this
 * listener covers the indirect ways a fight leaves marks: buckets, flowing water and lava,
 * fire, falling blocks and explosions.
 */
public final class ArenaResetListener implements Listener {

    private final PracticePlugin plugin;

    public ArenaResetListener(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    /** The match whose arena covers this block, or {@code null}. */
    private Match matchAt(Location location) {
        if (!plugin.hub().isPractice(location.getWorld())) {
            return null;
        }
        for (Match match : plugin.matches().running()) {
            if (match.arena().contains(location)) {
                return match;
            }
        }
        return null;
    }

    private void record(Block block) {
        Match match = matchAt(block.getLocation());
        if (match != null) {
            match.blocks().record(block);
        }
    }

    private void recordAll(List<Block> blocks) {
        for (Block block : blocks) {
            record(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        record(event.getBlock());
    }

    /** Water and lava spread on their own; every target block has to be remembered. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        record(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        record(event.getBlock());
    }

    /** Covers melting ice and snow left behind by a fight. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        record(event.getBlock());
    }

    /** Falling sand and gravel, and endermen picking blocks up. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        recordAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        recordAll(event.blockList());
    }

    /**
     * A player standing in an arena must not build outside of it, otherwise the rollback would
     * miss those blocks entirely.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceOutside(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.matches().matchOf(player);
        if (match == null || !match.isFighting() || !match.kit().build()) {
            return;
        }
        if (!match.arena().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "match.outside-arena");
        }
    }
}

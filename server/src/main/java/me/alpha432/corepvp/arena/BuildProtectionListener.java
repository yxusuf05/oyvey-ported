package me.alpha432.corepvp.arena;

import me.alpha432.corepvp.arena.rollback.BlockKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.Iterator;
import java.util.List;

/**
 * Records every change a match makes to an arena, and blocks the ones that are
 * not allowed.
 *
 * <p>The long list of handlers is the point. Placing and breaking are the
 * obvious sources, but a match also changes blocks through explosions, falling
 * sand, flowing water, fire, buckets and pistons. Any one of them missed here
 * is a block that never gets rolled back, and arenas drift a little further
 * from their original state with every match.
 */
public final class BuildProtectionListener implements Listener {

    /** Supplied by the match engine: whether this player's kit allows building. */
    @FunctionalInterface
    public interface BuildPolicy {
        boolean canBuild(Player player);
    }

    private final ArenaManager arenas;
    private BuildPolicy policy = player -> false;

    public BuildProtectionListener(ArenaManager arenas) {
        this.arenas = arenas;
    }

    public void policy(BuildPolicy policy) {
        this.policy = policy;
    }

    // ------------------------------------------------------------------
    //  Player driven
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        handlePlayerChange(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        Arena arena = arenas.arenaOf(event.getPlayer());
        if (arena == null) {
            return;
        }
        if (!allowed(event.getPlayer(), arena, event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (BlockState state : event.getReplacedBlockStates()) {
            record(arena, state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        handlePlayerChange(event.getPlayer(), event.getBlock(), event);
        if (!event.isCancelled()) {
            // Restoring the block later would duplicate whatever it dropped.
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        handlePlayerChange(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        handlePlayerChange(event.getPlayer(), event.getBlock(), event);
    }

    private void handlePlayerChange(Player player, Block block, org.bukkit.event.Cancellable event) {
        Arena arena = arenas.arenaOf(player);
        if (arena == null) {
            return;
        }
        if (!allowed(player, arena, block.getLocation())) {
            event.setCancelled(true);
            return;
        }
        record(arena, block);
    }

    private boolean allowed(Player player, Arena arena, Location location) {
        if (!policy.canBuild(player)) {
            return false;
        }
        Cuboid build = arena.buildBounds();
        return build == null || build.contains(location);
    }

    // ------------------------------------------------------------------
    //  Everything else that moves a block
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        recordExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        recordExplosion(event.blockList());
    }

    /**
     * Explosions carry no player, so affected blocks are matched to an arena by
     * position. Anything outside an active arena is taken out of the blast list
     * rather than left to damage the world permanently.
     */
    private void recordExplosion(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Arena arena = arenas.arenaAt(block.getLocation());
            if (arena == null) {
                iterator.remove();
                continue;
            }
            record(arena, block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        recordAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        recordAt(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        recordAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        recordAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        recordAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        recordAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        recordAt(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(this::recordAt);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(this::recordAt);
    }

    private void recordAt(Block block) {
        Arena arena = arenas.arenaAt(block.getLocation());
        if (arena != null) {
            record(arena, block);
        }
    }

    private void record(Arena arena, Block block) {
        arena.journal().record(
                BlockKey.pack(block.getX(), block.getY(), block.getZ()),
                block.getBlockData());
    }
}

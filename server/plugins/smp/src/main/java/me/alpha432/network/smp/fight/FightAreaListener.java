package me.alpha432.network.smp.fight;

import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces the rules of the spawn fight areas and records every block change so the crystal
 * area can be rolled back on its timer.
 */
public final class FightAreaListener implements Listener {

    private final SmpPlugin plugin;

    public FightAreaListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ item restrictions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        FightArea area = plugin.fightAreas().at(attacker.getLocation()).orElse(null);
        if (area == null || area.type() != FightArea.Type.SWORD) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!area.allows(weapon.getType())) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "fightarea.weapon-not-allowed");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        FightArea area = plugin.fightAreas().at(player.getLocation()).orElse(null);
        if (area == null || area.type() != FightArea.Type.SWORD) {
            return;
        }
        if (!area.allows(item.getType())) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "fightarea.item-not-allowed");
        }
    }

    // ------------------------------------------------------------------ building

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        FightArea area = plugin.fightAreas().at(event.getBlock().getLocation()).orElse(null);
        if (area == null) {
            return;
        }
        if (!area.allowsBuilding()) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "fightarea.no-build");
            return;
        }
        // Remember the air that was here so the reset can put it back.
        area.changes().record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        FightArea area = plugin.fightAreas().at(event.getBlock().getLocation()).orElse(null);
        if (area == null) {
            return;
        }
        if (!area.allowsBuilding()) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "fightarea.no-build");
            return;
        }
        area.changes().record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        FightArea area = plugin.fightAreas().at(event.getBlock().getLocation()).orElse(null);
        if (area == null) {
            return;
        }
        if (!area.allowsBuilding()) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "fightarea.no-build");
            return;
        }
        area.changes().record(event.getBlock());
    }

    // ------------------------------------------------------------------ explosions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        recordExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        recordExplosion(event.blockList());
    }

    /**
     * Crystals blow holes into the arena floor. Every affected block inside an area is recorded
     * before it disappears; blocks in a sword area are kept out of the blast entirely.
     */
    private void recordExplosion(java.util.List<Block> blocks) {
        blocks.removeIf(block -> {
            FightArea area = plugin.fightAreas().at(block.getLocation()).orElse(null);
            if (area == null) {
                return false;
            }
            if (!area.allowsBuilding()) {
                return true;
            }
            area.changes().record(block);
            return false;
        });
    }
}

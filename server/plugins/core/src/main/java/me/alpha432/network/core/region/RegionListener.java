package me.alpha432.network.core.region;

import me.alpha432.network.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Enforces every {@link RegionFlag}. Registered once by the core plugin. */
public final class RegionListener implements Listener {

    private final RegionService regions;

    public RegionListener(RegionService regions) {
        this.regions = regions;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (!regions.allows(event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BUILD)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (!regions.allows(event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BUILD)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!regions.allows(event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BUILD)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!regions.allows(event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BUILD)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!regions.allows(victim.getLocation(), RegionFlag.DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null) {
            return;
        }
        if (!regions.allows(victim.getLocation(), RegionFlag.PVP)
                || !regions.allows(attacker.getLocation(), RegionFlag.PVP)) {
            event.setCancelled(true);
            deny(attacker);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && !regions.allows(player.getLocation(), RegionFlag.HUNGER)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (!regions.allows(event.getPlayer(), event.getPlayer().getLocation(), RegionFlag.ITEM_DROP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && !regions.allows(player, player.getLocation(), RegionFlag.ITEM_PICKUP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (!regions.allows(event.getPlayer(), event.getClickedBlock().getLocation(), RegionFlag.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        if (!regions.allows(event.getLocation(), RegionFlag.MOB_SPAWN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !regions.allows(block.getLocation(), RegionFlag.EXPLOSIONS));
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private static void deny(Player player) {
        Core.messages().actionBar(player, "region.denied");
    }
}

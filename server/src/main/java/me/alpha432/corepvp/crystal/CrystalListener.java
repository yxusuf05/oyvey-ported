package me.alpha432.corepvp.crystal;

import me.alpha432.corepvp.CorePvPPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Wires crystal PvP into the rest of the plugin.
 *
 * <p>Everything here runs at LOW priority so attribution is in place before the
 * match engine decides, at HIGHEST, whether a hit was lethal and who gets the
 * kill.
 */
public final class CrystalListener implements Listener {

    private final CorePvPPlugin plugin;
    private final CrystalService crystals;

    public CrystalListener(CorePvPPlugin plugin, CrystalService crystals) {
        this.plugin = plugin;
        this.crystals = crystals;
    }

    /** End crystals are entities, so placing one is an entity event, not a block one. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal && event.getPlayer() != null) {
            crystals.rememberCrystal(crystal.getUniqueId(), event.getPlayer());
        }
    }

    /**
     * A charged respawn anchor used outside the Nether explodes. The resulting
     * BlockExplodeEvent has no player attached, so the position is recorded
     * here and matched up afterwards.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        crystals.rememberAnchor(event.getClickedBlock().getLocation(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof EnderCrystal crystal)) {
            return;
        }
        crystals.attribute(victim, crystals.placerOf(crystal.getUniqueId()), event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplosionDamage(EntityDamageByBlockEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        crystals.attribute(victim, crystals.anchorOwnerNear(victim.getLocation()), event.getFinalDamage());
    }

    /** Once a crystal has gone off there is nothing left to attribute to it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) {
            crystals.forgetCrystal(crystal.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            crystals.totemPopped(player);
        }
    }
}

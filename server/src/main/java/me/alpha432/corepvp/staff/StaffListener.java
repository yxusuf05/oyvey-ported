package me.alpha432.corepvp.staff;

import me.alpha432.corepvp.CorePvPPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Staff mode behaviour and the freeze that goes with it. */
public final class StaffListener implements Listener {

    private final CorePvPPlugin plugin;
    private final StaffService staff;

    public StaffListener(CorePvPPlugin plugin, StaffService staff) {
        this.plugin = plugin;
        this.staff = staff;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        staff.hideVanishedFrom(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        staff.forget(event.getPlayer().getUniqueId());
    }

    /** Frozen players may look around but not move. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!staff.isFrozen(event.getPlayer())) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()
                || event.getFrom().getY() < event.getTo().getY()) {
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && (staff.inStaffMode(player) || staff.isFrozen(player))) {
            event.setCancelled(true);
        }
    }

    /** Right-clicking a player in staff mode opens their inventory read-only. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!staff.inStaffMode(event.getPlayer())
                || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().openInventory(target.getInventory());
    }
}

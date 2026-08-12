package me.alpha432.corepvp.survival;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Survival's own rules: spawn protection, and no fighting inside it. */
public final class SurvivalListener implements Listener {

    private final CorePvPPlugin plugin;
    private final SurvivalService survival;

    public SurvivalListener(CorePvPPlugin plugin, SurvivalService survival) {
        this.plugin = plugin;
        this.survival = survival;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isSurvival(victim)) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player direct
                ? direct
                : event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker == null) {
            return;
        }
        // Protection covers both ends, so spawn cannot be used as a firing post.
        if (survival.inSpawnProtection(victim.getLocation())
                || survival.inSpawnProtection(attacker.getLocation())) {
            event.setCancelled(true);
            plugin.messages().send(attacker, "survival.spawn-protected");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isSurvival(event.getPlayer()) && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isSurvival(event.getPlayer()) && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Dying in survival puts the player back at the survival spawn, not the hub. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isSurvival(event.getPlayer())) {
            return;
        }
        if (survival.spawn() != null) {
            event.setRespawnLocation(survival.spawn());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        // Runs before the profile listener unloads anything.
        survival.saveState(event.getPlayer());
        survival.unload(event.getPlayer().getUniqueId());
    }

    private boolean protect(Player player) {
        return survival.inSpawnProtection(player.getLocation())
                && !player.hasPermission("corepvp.build");
    }

    private boolean isSurvival(Player player) {
        return plugin.states().is(player, PlayerState.SURVIVAL);
    }
}

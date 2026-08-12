package me.alpha432.corepvp.ffa;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.kit.Kit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Damage, respawning and safe zones in free-for-all arenas. */
public final class FfaListener implements Listener {

    private final CorePvPPlugin plugin;
    private final FfaService ffa;

    public FfaListener(CorePvPPlugin plugin, FfaService ffa) {
        this.plugin = plugin;
        this.ffa = ffa;
    }

    /** Records the hit, and refuses fights that start or land in the safe zone. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        FfaArena arena = ffa.arenaOf(victim);
        if (arena == null) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        if (attacker.equals(victim)) {
            return;
        }
        // Neither side may use the spawn platform as cover.
        if (arena.inSafeZone(victim.getLocation()) || arena.inSafeZone(attacker.getLocation())) {
            event.setCancelled(true);
            return;
        }
        ffa.tag(victim);
        ffa.tag(attacker);
    }

    /** Lethal damage becomes a respawn rather than a death screen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        FfaArena arena = ffa.arenaOf(victim);
        if (arena == null) {
            return;
        }
        if (arena.inSafeZone(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() < victim.getHealth()) {
            return;
        }

        event.setCancelled(true);
        ffa.handleDeath(victim, plugin.matches().resolveKiller(victim));
    }

    /** Falling off the platform counts as a death, not a trip to the void. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        FfaArena arena = ffa.arenaOf(player);
        if (arena == null || event.getTo().getY() > arena.deathY()) {
            return;
        }
        ffa.handleDeath(player, plugin.matches().resolveKiller(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Kit kit = kitOf(player);
        if (kit != null && !kit.flags().hunger()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Kit kit = kitOf(player);
        if (kit == null) {
            return;
        }
        boolean natural = event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN;
        if (natural && !kit.flags().naturalRegen()) {
            event.setCancelled(true);
        }
    }

    /** Safety net for deaths that slip past the damage interception. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!ffa.isPlaying(player)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        ffa.handleDeath(player, player.getKiller());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ffa.handleQuit(event.getPlayer());
    }

    private Kit kitOf(Player player) {
        FfaArena arena = ffa.arenaOf(player);
        return arena == null ? null : plugin.kits().byId(arena.kitId());
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}

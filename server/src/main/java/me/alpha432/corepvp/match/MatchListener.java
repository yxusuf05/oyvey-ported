package me.alpha432.corepvp.match;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Turns raw Bukkit events into match outcomes. */
public final class MatchListener implements Listener {

    private final CorePvPPlugin plugin;
    private final MatchManager matches;
    private final Messages messages;

    public MatchListener(CorePvPPlugin plugin, MatchManager matches) {
        this.plugin = plugin;
        this.matches = matches;
        this.messages = plugin.messages();
    }

    // ------------------------------------------------------------------
    //  Damage
    // ------------------------------------------------------------------

    /** Runs early: decides whether the hit counts at all, and records it. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }

        Match match = matches.matchOf(victim);
        if (match == null || !match.contains(attacker.getUniqueId())) {
            return;
        }
        if (match.state() != MatchState.FIGHTING || match.sameTeam(victim.getUniqueId(), attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        matches.tagAttacker(victim, attacker);
        MatchPlayerStats attackerStats = match.stats(attacker.getUniqueId());
        attackerStats.hit();
        attackerStats.damage(event.getFinalDamage());
        match.stats(victim.getUniqueId()).tookHit();

        if (event.getDamager() instanceof Projectile projectile && !(projectile instanceof ThrownPotion)) {
            attackerStats.arrowHit();
        }

        if (match.kit().flags().boxing()) {
            handleBoxingHit(match, attacker, victim, event);
        }
    }

    /**
     * Runs last, once every modifier has been applied: lethal damage is
     * cancelled and turned into our own death handling. That skips the respawn
     * screen, the item drop race and the vanilla death message, and keeps
     * killer attribution in one place.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Match match = matches.matchOf(victim);
        if (match == null) {
            return;
        }

        // No damage during the countdown or after the result is decided.
        if (match.state() != MatchState.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        Kit kit = match.kit();
        if (kit.flags().sumo() || kit.flags().boxing()) {
            // Both are decided by something other than health.
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() < victim.getHealth()) {
            return;
        }

        event.setCancelled(true);
        Player killer = event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent cause
                && cause.getDamager() instanceof Player direct
                ? direct
                : matches.resolveKiller(victim);
        matches.handleDeath(victim, killer);
    }

    private void handleBoxingHit(Match match, Player attacker, Player victim, EntityDamageByEntityEvent event) {
        event.setDamage(0.0D);
        int target = plugin.configs().main().getInt("match.boxing-hits", 100);
        int hits = match.stats(attacker.getUniqueId()).hits();
        if (hits < target) {
            return;
        }
        MatchTeam team = match.teamOf(attacker.getUniqueId());
        MatchTeam loser = match.teamOf(victim.getUniqueId());
        if (loser != null) {
            for (var uuid : loser.members()) {
                loser.kill(uuid);
            }
        }
        matches.end(match, team);
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

    // ------------------------------------------------------------------
    //  Kit rules
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match != null && !match.kit().flags().hunger()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        boolean natural = event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN;
        if (natural && !match.kit().flags().naturalRegen()) {
            event.setCancelled(true);
        }
    }

    /** Freezes players in place during the countdown, but lets them look around. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.states().is(event.getPlayer(), PlayerState.MATCH_STARTING)) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }

    // ------------------------------------------------------------------
    //  Statistics
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        if (event.getEntity() instanceof ThrownPotion) {
            match.stats(player.getUniqueId()).potionThrown();
        } else {
            match.stats(player.getUniqueId()).arrowShot();
        }
    }

    /**
     * A healing potion only counts as a hit when it actually healed the thrower,
     * which is what players mean by potion accuracy.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        if (event.getAffectedEntities().contains(player) && event.getIntensity(player) > 0.0D) {
            match.stats(player.getUniqueId()).potionHit();
        }
    }

    // ------------------------------------------------------------------
    //  Safety nets
    // ------------------------------------------------------------------

    /**
     * Damage is normally intercepted before it kills, but /kill and a few
     * exotic causes can still get through. Keeping this handler means a stray
     * death never drops a kit on the arena floor.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        event.setCancelled(false);
        matches.handleDeath(player, player.getKiller());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        matches.handleQuit(event.getPlayer());
    }
}

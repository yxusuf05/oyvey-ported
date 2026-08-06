package me.alpha432.network.practice.listener;

import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.kit.PracticeKit;
import me.alpha432.network.practice.match.Match;
import me.alpha432.network.practice.menu.PracticeMenu;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Turns the kit rules into actual behaviour and keeps the hub free of combat. */
public final class PracticeListener implements Listener {

    private final PracticePlugin plugin;

    public PracticeListener(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ session

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.stats().load(event.getUniqueId());
            plugin.kits().load(event.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.hub().isPractice(event.getPlayer())) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.hub().send(event.getPlayer()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.matches().matchOf(player);
        if (match != null && match.contains(player.getUniqueId())) {
            // Leaving mid fight counts as a loss.
            plugin.matches().end(match, match.opponentOf(player.getUniqueId()), "match.reason-quit");
        } else if (match != null) {
            plugin.matches().removeSpectator(player);
        }
        plugin.queue().leave(player);
        plugin.ffa().leave(player, false);
        me.alpha432.network.practice.command.DuelCommands.forget(player.getUniqueId());
        plugin.stats().unload(player.getUniqueId());
        plugin.kits().unload(player.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.hub().isPractice(player)) {
            plugin.hub().reset(player);
            return;
        }
        if (plugin.hub().isPractice(event.getFrom())) {
            plugin.queue().leave(player);
            plugin.ffa().leave(player, false);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.hub().isPractice(player)) {
            return;
        }
        if (plugin.ffa().isPlaying(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.ffa().respawn(player));
            return;
        }
        if (plugin.hub().spawn() != null) {
            event.setRespawnLocation(plugin.hub().spawn());
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.hub().reset(player));
    }

    // ------------------------------------------------------------------ combat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.hub().isPractice(player)) {
            return;
        }
        if (plugin.ffa().isPlaying(player)) {
            return;
        }
        Match match = plugin.matches().matchOf(player);
        if (match == null || plugin.matches().isSpectator(player)) {
            // Nobody takes damage in the hub or while spectating.
            event.setCancelled(true);
            return;
        }
        if (!match.isFighting()) {
            event.setCancelled(true);
            return;
        }
        if (match.kit().sumo()) {
            // Sumo is decided by falling out, not by hearts.
            event.setDamage(0.0D);
        }
        if (match.kit().boxing()) {
            event.setDamage(0.0D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null) {
            return;
        }
        Match match = plugin.matches().matchOf(victim);
        if (match == null || !match.isFighting() || !match.contains(attacker.getUniqueId())) {
            return;
        }
        int hits = match.registerHit(attacker.getUniqueId(), victim.getUniqueId());
        if (match.kit().boxing() && hits >= match.kit().hitsToWin()) {
            plugin.matches().end(match, attacker.getUniqueId(), "match.reason-boxing");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.hub().isPractice(player)) {
            return;
        }
        event.setCancelled(false);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);

        if (plugin.ffa().isPlaying(player)) {
            Player killer = player.getKiller();
            if (killer != null && plugin.ffa().isPlaying(killer)) {
                plugin.ffa().registerKill(killer);
            }
            return;
        }
        Match match = plugin.matches().matchOf(player);
        if (match != null && match.contains(player.getUniqueId())) {
            plugin.matches().end(match, match.opponentOf(player.getUniqueId()), "match.reason-death");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.hub().isPractice(player)) {
            return;
        }
        Match match = plugin.matches().matchOf(player);
        boolean hungerAllowed = match != null && match.kit().hunger();
        if (!hungerAllowed) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    // ------------------------------------------------------------------ movement

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.hub().isPractice(player) || event.getTo() == null) {
            return;
        }
        Match match = plugin.matches().matchOf(player);
        if (match == null || !match.contains(player.getUniqueId())) {
            return;
        }
        if (match.state() == Match.State.STARTING) {
            // Freeze during the countdown, but let players look around.
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getZ() != event.getTo().getZ()
                    || event.getTo().getY() > event.getFrom().getY()) {
                event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
            }
            return;
        }
        if (!match.isFighting()) {
            return;
        }
        PracticeKit kit = match.kit();
        if (kit.voidLevel() != Integer.MIN_VALUE && event.getTo().getY() < kit.voidLevel()) {
            plugin.matches().end(match, match.opponentOf(player.getUniqueId()),
                    kit.sumo() ? "match.reason-sumo" : "match.reason-void");
        }
    }

    // ------------------------------------------------------------------ building

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.hub().isPractice(player) || player.hasPermission("network.practice.admin")) {
            return;
        }
        Match match = plugin.matches().matchOf(player);
        if (match == null || !match.isFighting() || !match.kit().build()
                || !match.arena().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        // Remember the air that was there so the arena can be rolled back.
        match.blocks().record(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.hub().isPractice(player) || player.hasPermission("network.practice.admin")) {
            return;
        }
        Match match = plugin.matches().matchOf(player);
        if (match == null || !match.isFighting() || !match.kit().build()
                || !match.arena().contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        match.blocks().record(event.getBlock());
    }

    // ------------------------------------------------------------------ hub items

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.hub().isPractice(player)) {
            return;
        }
        if (plugin.matches().isInMatch(player) || plugin.ffa().isPlaying(player)) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        String action = plugin.hub().actionOf(event.getItem());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        switch (action) {
            case "menu" -> new PracticeMenu(plugin, player).open(player);
            case "unranked" -> new me.alpha432.network.practice.menu.QueueMenu(plugin, player, false)
                    .open(player);
            case "ranked" -> new me.alpha432.network.practice.menu.QueueMenu(plugin, player, true)
                    .open(player);
            case "ffa" -> new me.alpha432.network.practice.menu.FfaMenu(plugin, player).open(player);
            case "editor" -> new me.alpha432.network.practice.menu.KitEditorMenu(plugin, player)
                    .open(player);
            case "leave" -> player.performCommand("leave");
            default -> plugin.getLogger().warning("Unknown hub item action: " + action);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.hub().isPractice(player)) {
            return;
        }
        if (plugin.matches().isInMatch(player) && !plugin.matches().isSpectator(player)) {
            return;
        }
        if (plugin.ffa().isPlaying(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.hub().isPractice(player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }
        boolean inCombat = (plugin.matches().isInMatch(player) && !plugin.matches().isSpectator(player))
                || plugin.ffa().isPlaying(player);
        if (!inCombat && event.getInventory().getHolder() == null) {
            // The hub inventory holds the menu items and must stay untouched.
            event.setCancelled(true);
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
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

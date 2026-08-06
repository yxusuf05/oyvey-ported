package me.alpha432.network.lobby.listener;

import me.alpha432.network.core.rank.Permissions;
import me.alpha432.network.lobby.LobbyPlugin;
import me.alpha432.network.lobby.menu.SelectorMenu;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

/** Everything that makes the lobby a lobby: spawn on join, protection and the hotbar items. */
public final class LobbyListener implements Listener {

    private final LobbyPlugin plugin;

    public LobbyListener(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("teleport-on-join", true)) {
            // Runs a tick later so the teleport wins against the vanilla spawn placement.
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.lobby().send(player);
                prepare(player);
            });
        } else if (plugin.lobby().isLobby(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> prepare(player));
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.lobby().isLobby(player)) {
            prepare(player);
        } else if (plugin.lobby().isLobby(event.getFrom())) {
            // Leaving the lobby: hand the player back to whatever plugin owns the target world.
            player.getInventory().clear();
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    /** Resets a player to the lobby state: clean inventory, hotbar items, flight rules. */
    public void prepare(Player player) {
        if (!plugin.lobby().isLobby(player)) {
            return;
        }
        if (plugin.getConfig().getBoolean("join-items.clear-inventory", true)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
        }
        String gameMode = plugin.getConfig().getString("join-items.gamemode", "ADVENTURE");
        try {
            player.setGameMode(GameMode.valueOf(gameMode.toUpperCase()));
        } catch (IllegalArgumentException e) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0D : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setLevel(0);
        player.setExp(0f);

        if (plugin.getConfig().getBoolean("join-items.enabled", true)) {
            plugin.joinItems().give(player);
            plugin.joinItems().updateVisibilityItem(player, plugin.visibility().isVisible(player));
        }
        // Flight is a rank perk; the double jump needs allowFlight even without it.
        boolean canFly = player.hasPermission("network.lobby.fly");
        player.setAllowFlight(canFly || plugin.getConfig().getBoolean("double-jump.enabled", true));
        player.setFlying(canFly && player.getAllowFlight() && plugin.getConfig()
                .getBoolean("fly-by-default", false));
        plugin.visibility().apply(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.lobby().isLobby(event.getPlayer())) {
            return;
        }
        if (plugin.lobby().spawn() != null) {
            event.setRespawnLocation(plugin.lobby().spawn());
        }
        Bukkit.getScheduler().runTask(plugin, () -> prepare(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.lobby().isLobby(player)
                && plugin.getConfig().getBoolean("protection.invulnerable", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.lobby().isLobby(player)
                && plugin.getConfig().getBoolean("protection.no-hunger", true)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.lobby().isLobby(event.getPlayer())
                && plugin.getConfig().getBoolean("protection.no-drop", true)
                && !event.getPlayer().hasPermission("network.lobby.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.lobby().isLobby(player)
                && plugin.getConfig().getBoolean("protection.no-pickup", true)
                && !player.hasPermission("network.lobby.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.lobby().isLobby(player)
                && plugin.getConfig().getBoolean("protection.lock-inventory", true)
                && !player.hasPermission("network.lobby.admin")) {
            event.setCancelled(true);
        }
    }

    /** The void is not a death trap in the lobby, it is a lift back to spawn. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.lobby().isLobby(event.getPlayer())) {
            return;
        }
        int minY = plugin.getConfig().getInt("protection.void-teleport-y", 0);
        if (event.getTo().getY() < minY) {
            plugin.lobby().send(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.lobby().isLobby(player) || event.getItem() == null) {
            return;
        }
        String action = plugin.joinItems().actionOf(event.getItem());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        switch (action) {
            case "selector" -> new SelectorMenu(plugin, player).open(player);
            case "visibility" -> plugin.visibility().toggle(player);
            case "ranks" -> player.performCommand("rank menu");
            default -> plugin.getLogger().warning("Unknown join item action: " + action);
        }
    }

    private boolean blocked(Player player) {
        return plugin.lobby().isLobby(player)
                && plugin.getConfig().getBoolean("protection.no-build", true)
                && !player.hasPermission("network.lobby.admin");
    }

    /** Exposed for the double jump listener, which reuses the boost tier permission. */
    public static double boostFor(Player player) {
        return Permissions.highest(player, "network.lobby.doublejump.tier.", 0);
    }
}

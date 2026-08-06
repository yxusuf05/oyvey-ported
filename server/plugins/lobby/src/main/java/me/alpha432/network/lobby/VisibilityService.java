package me.alpha432.network.lobby;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Lets a player hide everyone else in the lobby so the hub stays quiet. */
public final class VisibilityService implements Listener {

    private final LobbyPlugin plugin;
    private final Set<UUID> hiding = new HashSet<>();

    public VisibilityService(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVisible(Player player) {
        return !hiding.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        boolean nowVisible = hiding.remove(player.getUniqueId());
        if (!nowVisible) {
            hiding.add(player.getUniqueId());
        }
        apply(player);
        plugin.joinItems().updateVisibilityItem(player, nowVisible);
        plugin.messages().send(player, nowVisible ? "visibility.shown" : "visibility.hidden");
    }

    /** Applies the player's own choice, and makes sure others see them again. */
    public void apply(Player player) {
        boolean visible = isVisible(player);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (visible || !plugin.lobby().isLobby(player)) {
                player.showPlayer(plugin, other);
            } else {
                player.hidePlayer(plugin, other);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Everyone who hid other players must also not see the one that just joined.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(event.getPlayer()) && !isVisible(online) && plugin.lobby().isLobby(online)) {
                online.hidePlayer(plugin, event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiding.remove(event.getPlayer().getUniqueId());
    }
}

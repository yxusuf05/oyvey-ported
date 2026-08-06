package me.alpha432.network.lobby.listener;

import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.lobby.LobbyPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

/** Double jump in the lobby: the second jump turns into a forward boost. */
public final class DoubleJumpListener implements Listener {

    private final LobbyPlugin plugin;

    public DoubleJumpListener(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!plugin.lobby().isLobby(player)
                || !plugin.getConfig().getBoolean("double-jump.enabled", true)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // Players with the fly perk keep real flight instead of the boost.
        if (player.hasPermission("network.lobby.fly")) {
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        double forward = plugin.getConfig().getDouble("double-jump.velocity", 1.0D);
        if (player.hasPermission("network.lobby.doublejump.boost")) {
            forward = plugin.getConfig().getDouble("double-jump.boost-velocity", 1.4D);
        }
        double up = plugin.getConfig().getDouble("double-jump.up-velocity", 0.9D);
        Vector direction = player.getLocation().getDirection().multiply(forward).setY(up);
        player.setVelocity(direction);
        Sounds.play(player, plugin.getConfig().getString("double-jump.sound", ""), 0.6f, 1.2f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("double-jump.enabled", true)
                || !plugin.lobby().isLobby(player)
                || player.getAllowFlight()
                || player.hasPermission("network.lobby.fly")) {
            return;
        }
        if (player.isOnGround()) {
            player.setAllowFlight(true);
        }
    }
}

package me.alpha432.network.smp.listener;

import me.alpha432.network.core.Core;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.kit.Kit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Loads kit cooldowns, hands out the starter kit and keeps deaths tied to the SMP spawn. */
public final class SmpListener implements Listener {

    private final SmpPlugin plugin;

    public SmpListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.kits().load(event.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.kits().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveFirstJoinKits(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        giveFirstJoinKits(event.getPlayer());
    }

    /** First-join kits are handed out the first time the player stands in the SMP. */
    private void giveFirstJoinKits(Player player) {
        if (!plugin.spawn().isSmp(player)) {
            return;
        }
        for (Kit kit : plugin.kits().all()) {
            if (!kit.firstJoin() || plugin.kits().hasClaimed(player.getUniqueId(), kit)) {
                continue;
            }
            if (kit.permission() != null && !player.hasPermission(kit.permission())) {
                continue;
            }
            plugin.kits().give(player, kit);
            plugin.messages().send(player, "kit.first-join", "<name>", kit.displayName());
        }
    }

    /** Remember where the player died so /back brings them there. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.spawn().isSmp(event.getEntity())) {
            Core.teleports().pushBack(event.getEntity(), event.getEntity().getLocation());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.spawn().isSmp(event.getRespawnLocation().getWorld())) {
            return;
        }
        // Only override when the player has no bed or anchor to return to.
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spawn = plugin.spawn().spawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}

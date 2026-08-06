package me.alpha432.network.staff.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.alpha432.network.staff.StaffPlugin;
import me.alpha432.network.staff.punishment.Punishment;
import me.alpha432.network.staff.punishment.PunishmentType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

/** Keeps banned players out and muted players quiet. */
public final class StaffListener implements Listener {

    private final StaffPlugin plugin;

    public StaffListener(StaffPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Punishment ban = plugin.punishments().activeOf(event.getUniqueId(), PunishmentType.BAN);
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.banScreen(ban));
            return;
        }
        // Warm the mute cache while we are already off the main thread.
        plugin.punishments().cacheMute(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.isStaffChatting(player)) {
            event.setCancelled(true);
            plugin.sendStaffChat(player.getName(),
                    PlainTextComponentSerializer.plainText().serialize(event.message()));
            return;
        }
        Punishment mute = plugin.punishments().activeOf(player.getUniqueId(), PunishmentType.MUTE);
        if (mute != null) {
            event.setCancelled(true);
            plugin.sendMuteNotice(player, mute);
        }
    }

    /** Muted players must not route around the mute with /msg or /me. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (!plugin.getConfig().getStringList("muted-blocked-commands").contains(command)) {
            return;
        }
        Punishment mute = plugin.punishments().activeOf(player.getUniqueId(), PunishmentType.MUTE);
        if (mute != null) {
            event.setCancelled(true);
            plugin.sendMuteNotice(player, mute);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.clearStaffChat(event.getPlayer());
        plugin.punishments().forget(event.getPlayer().getUniqueId());
    }
}

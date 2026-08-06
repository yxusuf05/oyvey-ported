package me.alpha432.network.chat.listener;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.Core;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.core.text.Placeholders;
import me.alpha432.network.core.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join and leave announcements plus the welcome message for first time players. */
public final class JoinQuitListener implements Listener {

    private final ChatPlugin plugin;

    public JoinQuitListener(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("join-quit.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        PlayerProfile profile = Core.profiles().get(player);
        boolean firstJoin = profile != null && profile.isNew();

        String message = plugin.getConfig().getString(
                firstJoin ? "join-quit.first-join" : "join-quit.join", "");
        event.joinMessage(message.isBlank() ? null : Msg.mm(Placeholders.apply(player, message)));

        for (String line : plugin.getConfig().getStringList("join-quit.welcome")) {
            player.sendMessage(Msg.mm(Placeholders.apply(player, line)));
        }
        if (firstJoin) {
            String sound = plugin.getConfig().getString("join-quit.first-join-sound", "");
            Bukkit.getOnlinePlayers().forEach(online -> Sounds.play(online, sound, 1f, 1f));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("join-quit.enabled", true)) {
            return;
        }
        String message = plugin.getConfig().getString("join-quit.quit", "");
        event.quitMessage(message.isBlank() ? null : Msg.mm(Placeholders.apply(event.getPlayer(), message)));
    }
}

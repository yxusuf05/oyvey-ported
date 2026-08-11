package me.alpha432.corepvp.profile;

import me.alpha432.corepvp.config.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/** Loads a profile before the player is let in, saves it when they leave. */
public final class ProfileListener implements Listener {

    private final Plugin plugin;
    private final ProfileManager profiles;
    private final Messages messages;
    private final boolean kickOnFailure;

    public ProfileListener(Plugin plugin, ProfileManager profiles, Messages messages, boolean kickOnFailure) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.messages = messages;
        this.kickOnFailure = kickOnFailure;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            profiles.loadBlocking(event.getUniqueId(), event.getName());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load the profile of " + event.getName(), exception);
            if (kickOnFailure) {
                // Letting them in with a blank profile would overwrite their real
                // stats on the next save, which is worse than a failed login.
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        messages.render("general.profile-load-failed"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        profiles.unload(event.getPlayer().getUniqueId());
    }
}

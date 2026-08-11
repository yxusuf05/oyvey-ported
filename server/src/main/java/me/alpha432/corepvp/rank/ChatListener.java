package me.alpha432.corepvp.rank;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.util.Cooldown;
import me.alpha432.corepvp.util.Text;
import me.alpha432.corepvp.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.concurrent.TimeUnit;

/** Applies the rank prefix to chat and rate-limits it. */
public final class ChatListener implements Listener {

    private final RankManager ranks;
    private final Messages messages;
    private final ConfigManager configs;
    private final Cooldown chatCooldown = new Cooldown();

    public ChatListener(RankManager ranks, Messages messages, ConfigManager configs) {
        this.ranks = ranks;
        this.messages = messages;
        this.configs = configs;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        long cooldownMillis = TimeUnit.SECONDS.toMillis(
                configs.main().getLong("chat.cooldown-seconds", 0L));
        if (cooldownMillis > 0L && !sender.hasPermission("corepvp.chat.bypass-cooldown")
                && !chatCooldown.tryUse(sender.getUniqueId(), cooldownMillis)) {
            event.setCancelled(true);
            messages.send(sender, "chat.cooldown", Messages.of("time",
                    TimeUtil.seconds(chatCooldown.remainingMillis(sender.getUniqueId()))));
            return;
        }

        String format = configs.main().getString("chat.format",
                "<rank_prefix><player><dark_gray>: <gray><message>");

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Rank rank = ranks.of(source);
            Component prefix = rank.prefix().isEmpty()
                    ? Component.empty()
                    : Text.mini(rank.prefix());
            // The name is coloured here rather than through a colour placeholder:
            // a MiniMessage placeholder inserts a component, it does not open a
            // colour scope for the text that follows it.
            Component name = Component.text(source.getName()).color(rank.color());
            return messages.parse(format,
                    Messages.of("rank_prefix", prefix),
                    Messages.of("player", name),
                    Messages.of("message", message));
        });
    }
}

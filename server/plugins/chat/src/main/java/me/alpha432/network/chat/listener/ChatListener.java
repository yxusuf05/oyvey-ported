package me.alpha432.network.chat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.core.text.Placeholders;
import me.alpha432.network.core.util.Cooldowns;
import me.alpha432.network.core.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Formats chat, blocks spam and highlights mentions. */
public final class ChatListener implements Listener {

    private final ChatPlugin plugin;
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<UUID, String> lastMessage = new HashMap<>();

    public ChatListener(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (plugin.isChatMuted() && !sender.hasPermission("network.chat.bypass-mute")) {
            event.setCancelled(true);
            plugin.messages().send(sender, "chat.muted");
            return;
        }
        if (!sender.hasPermission("network.chat.bypass-cooldown")) {
            long seconds = plugin.getConfig().getLong("cooldown-seconds", 2L);
            if (cooldowns.isActive(sender.getUniqueId())) {
                event.setCancelled(true);
                plugin.messages().send(sender, "chat.cooldown",
                        "<seconds>", String.valueOf(cooldowns.remainingSeconds(sender.getUniqueId())));
                return;
            }
            if (plugin.getConfig().getBoolean("block-repeated-messages", true)
                    && raw.equalsIgnoreCase(lastMessage.get(sender.getUniqueId()))) {
                event.setCancelled(true);
                plugin.messages().send(sender, "chat.repeated");
                return;
            }
            cooldowns.setSeconds(sender.getUniqueId(), seconds);
        }
        lastMessage.put(sender.getUniqueId(), raw);

        // Players who ignore the sender never see the line.
        event.viewers().removeIf(viewer ->
                viewer instanceof Player player && plugin.privateMessages().isIgnoring(player, sender));

        String body = sender.hasPermission("network.chat.color") ? raw : Msg.escape(raw);
        String highlighted = highlightMentions(sender, body);
        String format = plugin.getConfig().getString("format.chat", "%player%<gray>: <message>");
        Component rendered = Msg.mm(Placeholders.apply(sender, format).replace("<message>", highlighted));

        event.renderer((source, sourceDisplayName, message, viewer) -> rendered);
    }

    /**
     * Wraps the names of online players in the configured highlight and pings them. The ping is
     * scheduled on the main thread because chat events fire asynchronously.
     */
    private String highlightMentions(Player sender, String message) {
        if (!plugin.getConfig().getBoolean("mention.enabled", true)) {
            return message;
        }
        String highlight = plugin.getConfig().getString("mention.highlight", "<yellow>");
        String result = message;
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (online.equals(sender) || !containsIgnoreCase(result, name)) {
                continue;
            }
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(name),
                    java.util.regex.Matcher.quoteReplacement(highlight + name + "<reset><gray>"));
            Bukkit.getScheduler().runTask(plugin, () -> ping(online));
        }
        return result;
    }

    private void ping(Player target) {
        Sounds.play(target, plugin.getConfig().getString("mention.sound", ""), 1f, 1.4f);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
        cooldowns.clear(event.getPlayer().getUniqueId());
    }
}

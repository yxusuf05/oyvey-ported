package me.alpha432.network.chat;

import me.alpha432.network.core.text.Msg;
import me.alpha432.network.core.text.Placeholders;
import me.alpha432.network.core.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps track of who last messaged whom and who ignores whom. */
public final class PrivateMessages implements Listener {

    private final ChatPlugin plugin;
    private final Map<UUID, UUID> lastPartner = new HashMap<>();
    private final Map<UUID, Set<UUID>> ignored = new HashMap<>();

    public PrivateMessages(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return false when the target ignores the sender. */
    public boolean send(Player sender, Player target, String rawMessage) {
        String message = sender.hasPermission("network.chat.color")
                ? rawMessage
                : Msg.escape(rawMessage);

        String toSender = plugin.getConfig().getString("private-messages.format-sender", "");
        String toTarget = plugin.getConfig().getString("private-messages.format-target", "");

        sender.sendMessage(Msg.mm(Placeholders.apply(sender, toSender)
                .replace("%target%", target.getName())
                .replace("%sender%", sender.getName())
                .replace("<message>", message)));

        if (isIgnoring(target, sender)) {
            // The sender still sees their own line so ignoring stays invisible.
            return false;
        }

        target.sendMessage(Msg.mm(Placeholders.apply(target, toTarget)
                .replace("%target%", target.getName())
                .replace("%sender%", sender.getName())
                .replace("<message>", message)));
        playPing(target);

        lastPartner.put(sender.getUniqueId(), target.getUniqueId());
        lastPartner.put(target.getUniqueId(), sender.getUniqueId());
        return true;
    }

    public Player lastPartner(Player player) {
        UUID partner = lastPartner.get(player.getUniqueId());
        return partner == null ? null : Bukkit.getPlayer(partner);
    }

    public boolean isIgnoring(Player player, Player other) {
        Set<UUID> list = ignored.get(player.getUniqueId());
        return list != null && list.contains(other.getUniqueId())
                && !other.hasPermission("network.staff");
    }

    /** @return true when the player is now ignored, false when the ignore was lifted. */
    public boolean toggleIgnore(Player player, UUID target) {
        Set<UUID> list = ignored.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        if (!list.add(target)) {
            list.remove(target);
            return false;
        }
        return true;
    }

    public Set<UUID> ignoredBy(Player player) {
        return ignored.getOrDefault(player.getUniqueId(), Set.of());
    }

    private void playPing(Player target) {
        Sounds.play(target, plugin.getConfig().getString("private-messages.sound", ""), 1f, 1f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPartner.remove(uuid);
        ignored.remove(uuid);
        lastPartner.values().removeIf(uuid::equals);
    }
}

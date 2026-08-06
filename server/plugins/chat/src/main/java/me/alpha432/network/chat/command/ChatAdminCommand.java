package me.alpha432.network.chat.command;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.SubCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /chat clear|mute|unmute|reload} */
public final class ChatAdminCommand extends BaseCommand {

    public ChatAdminCommand(ChatPlugin plugin) {
        super(plugin, "chat");
        permission("network.chat.admin");

        sub(new SubCommand("clear") {
            @Override
            public void run(CommandSender sender, String[] args) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("network.chat.admin")) {
                        continue;
                    }
                    for (int i = 0; i < 100; i++) {
                        player.sendMessage(Component.empty());
                    }
                }
                Bukkit.broadcast(plugin.messages().get("chat.cleared", "<player>", sender.getName()));
            }
        }.description("Leert den Chat für alle"));

        sub(new SubCommand("mute") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.setChatMuted(true);
                Bukkit.broadcast(plugin.messages().get("chat.mute-broadcast", "<player>", sender.getName()));
            }
        }.description("Sperrt den globalen Chat"));

        sub(new SubCommand("unmute") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.setChatMuted(false);
                Bukkit.broadcast(plugin.messages().get("chat.unmute-broadcast", "<player>", sender.getName()));
            }
        }.description("Gibt den globalen Chat wieder frei"));

        sub(new SubCommand("reload") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.reloadEverything();
                plugin.messages().send(sender, "chat.reloaded");
            }
        }.description("Lädt die Chat-Konfiguration neu"));
    }
}

package me.alpha432.network.chat.command;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.UUID;

/** {@code /ignore <player>} and {@code /ignore list} */
public final class IgnoreCommand extends BaseCommand {

    private final ChatPlugin chat;

    public IgnoreCommand(ChatPlugin plugin) {
        super(plugin, "ignore");
        this.chat = plugin;
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            chat.messages().send(sender, "ignore.usage");
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            var ignored = chat.privateMessages().ignoredBy(player(sender));
            if (ignored.isEmpty()) {
                chat.messages().send(sender, "ignore.list-empty");
                return;
            }
            chat.messages().send(sender, "ignore.list-header", "<count>", String.valueOf(ignored.size()));
            for (UUID uuid : ignored) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                chat.messages().send(sender, "ignore.list-line",
                        "<name>", offline.getName() == null ? uuid.toString() : offline.getName());
            }
            return;
        }

        Core.profiles().lookup(args[0]).thenAccept(profile -> Bukkit.getScheduler().runTask(getPlugin(), () -> {
            if (profile == null) {
                chat.messages().send(sender, "ignore.not-found", "<name>", args[0]);
                return;
            }
            if (profile.uuid().equals(player(sender).getUniqueId())) {
                chat.messages().send(sender, "ignore.self");
                return;
            }
            boolean nowIgnored = chat.privateMessages().toggleIgnore(player(sender), profile.uuid());
            chat.messages().send(sender, nowIgnored ? "ignore.added" : "ignore.removed",
                    "<name>", profile.name());
        }));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> options = new java.util.ArrayList<>(CommandUtil.onlineNames(sender));
            options.add("list");
            return options;
        }
        return List.of();
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return chat;
    }
}

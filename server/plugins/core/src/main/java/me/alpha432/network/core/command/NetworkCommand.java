package me.alpha432.network.core.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** {@code /network reload|save|info} */
public final class NetworkCommand extends BaseCommand {

    public NetworkCommand(CorePlugin plugin) {
        super(plugin, "network");
        permission("network.command.network");

        sub(new SubCommand("reload") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.reloadEverything();
                Core.messages().send(sender, "network.reloaded");
            }
        }.description("Reloads the core configuration files"));

        sub(new SubCommand("save") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Core.profiles().saveAll();
                Core.regions().save();
                Core.messages().send(sender, "network.saved");
            }
        }.description("Writes profiles and regions to disk"));

        sub(new SubCommand("info") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Core.messages().send(sender, "network.info",
                        "<version>", plugin.getPluginMeta().getVersion(),
                        "<storage>", Core.database().isSqlite() ? "SQLite" : "MySQL",
                        "<players>", String.valueOf(Bukkit.getOnlinePlayers().size()),
                        "<profiles>", String.valueOf(Core.profiles().cached().size()),
                        "<worlds>", String.valueOf(Bukkit.getWorlds().size()),
                        "<regions>", String.valueOf(Core.regions().all().size()));
            }
        }.description("Shows the current state of the network"));
    }
}

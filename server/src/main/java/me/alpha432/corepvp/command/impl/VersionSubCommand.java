package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public final class VersionSubCommand extends SubCommand {

    private final CorePvPPlugin plugin;

    public VersionSubCommand(CorePvPPlugin plugin) {
        super("version", null, "version", "Show plugin and server version.", false, "ver");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.messages().send(sender, "admin.version",
                Messages.of("version", plugin.getPluginMeta().getVersion()),
                Messages.of("server", Bukkit.getMinecraftVersion()));
    }
}

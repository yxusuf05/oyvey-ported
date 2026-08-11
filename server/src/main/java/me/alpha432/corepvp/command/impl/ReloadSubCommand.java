package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import org.bukkit.command.CommandSender;

public final class ReloadSubCommand extends SubCommand {

    private final CorePvPPlugin plugin;

    public ReloadSubCommand(CorePvPPlugin plugin) {
        super("reload", "corepvp.command.admin", "reload", "Reload configuration and messages.", false, "rl");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        long start = System.currentTimeMillis();
        try {
            plugin.reloadConfiguration();
            plugin.messages().send(sender, "general.reloaded",
                    Messages.of("ms", System.currentTimeMillis() - start));
        } catch (Exception exception) {
            plugin.messages().send(sender, "general.reload-failed",
                    Messages.of("error", String.valueOf(exception.getMessage())));
            plugin.getLogger().warning("Reload failed: " + exception);
        }
    }
}

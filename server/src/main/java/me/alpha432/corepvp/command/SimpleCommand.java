package me.alpha432.corepvp.command;

import me.alpha432.corepvp.config.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A top-level command with no subcommands, e.g. {@code /duel}. */
public abstract class SimpleCommand implements CommandExecutor, TabCompleter {

    protected final Messages messages;
    private final String permission;
    private final boolean playerOnly;

    protected SimpleCommand(Messages messages, String permission, boolean playerOnly) {
        this.messages = messages;
        this.permission = permission;
        this.playerOnly = playerOnly;
    }

    protected abstract void run(CommandSender sender, String label, String[] args);

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        if (permission != null && !sender.hasPermission(permission)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (playerOnly && !(sender instanceof Player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        run(sender, label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}

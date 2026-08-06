package me.alpha432.network.core.command;

import me.alpha432.network.core.Core;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base for every command in the network. Handles permission and player-only checks, sub command
 * dispatch and tab completion, so implementations only contain the actual behaviour.
 *
 * <p>The command still has to be declared in the plugin's {@code plugin.yml}.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final JavaPlugin plugin;
    private final String name;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private String permission;
    private boolean playerOnly;

    protected BaseCommand(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    /** Runs when no sub command matched. The default reports the usage of the known ones. */
    protected void run(CommandSender sender, String[] args) {
        sendUsage(sender);
    }

    /** Completion for {@link #run}. Only consulted when no sub command matched. */
    protected List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    public BaseCommand permission(String permission) {
        this.permission = permission;
        return this;
    }

    public BaseCommand playerOnly() {
        this.playerOnly = true;
        return this;
    }

    public BaseCommand sub(SubCommand subCommand) {
        subCommands.put(subCommand.name(), subCommand);
        return this;
    }

    /** Wires the command up. Logs instead of throwing when the plugin.yml entry is missing. */
    public void register() {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().severe("Command /" + name + " is missing from plugin.yml");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!checkAccess(sender, permission, playerOnly)) {
            return true;
        }
        if (args.length > 0) {
            SubCommand sub = findSub(args[0]);
            if (sub != null) {
                if (!checkAccess(sender, sub.permission(), sub.isPlayerOnly())) {
                    return true;
                }
                sub.run(sender, Arrays.copyOfRange(args, 1, args.length));
                return true;
            }
        }
        run(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (permission != null && !sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        if (args.length == 1 && !subCommands.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (SubCommand sub : subCommands.values()) {
                if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                    names.add(sub.name());
                }
            }
            names.addAll(complete(sender, args));
            return CommandUtil.filter(args[0], names);
        }
        if (args.length > 1) {
            SubCommand sub = findSub(args[0]);
            if (sub != null) {
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
                    return Collections.emptyList();
                }
                return CommandUtil.filter(args[args.length - 1],
                        sub.complete(sender, Arrays.copyOfRange(args, 1, args.length)));
            }
        }
        return CommandUtil.filter(args.length == 0 ? "" : args[args.length - 1], complete(sender, args));
    }

    protected void sendUsage(CommandSender sender) {
        if (subCommands.isEmpty()) {
            return;
        }
        Core.messages().send(sender, "command.usage-header", "<command>", name);
        for (SubCommand sub : subCommands.values()) {
            if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
                continue;
            }
            Core.messages().send(sender, "command.usage-line",
                    "<command>", name,
                    "<sub>", sub.name(),
                    "<usage>", sub.usage(),
                    "<description>", sub.description());
        }
    }

    private boolean checkAccess(CommandSender sender, String requiredPermission, boolean requiresPlayer) {
        if (requiredPermission != null && !sender.hasPermission(requiredPermission)) {
            Core.messages().send(sender, "error.no-permission");
            return false;
        }
        if (requiresPlayer && !(sender instanceof Player)) {
            Core.messages().send(sender, "error.player-only");
            return false;
        }
        return true;
    }

    private SubCommand findSub(String input) {
        for (SubCommand sub : subCommands.values()) {
            if (sub.matches(input)) {
                return sub;
            }
        }
        return null;
    }

    protected static Player player(CommandSender sender) {
        return (Player) sender;
    }
}

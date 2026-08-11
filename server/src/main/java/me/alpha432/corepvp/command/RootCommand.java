package me.alpha432.corepvp.command;

import me.alpha432.corepvp.config.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A command with subcommands, wired to an entry in plugin.yml.
 *
 * <p>Deliberately plain Bukkit: Paper's Brigadier API is still marked
 * experimental, and this is ~100 lines with stable behaviour.
 */
public final class RootCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final Map<String, SubCommand> byName = new LinkedHashMap<>();
    private final List<SubCommand> ordered = new ArrayList<>();

    public RootCommand(Messages messages) {
        this.messages = messages;
    }

    public RootCommand register(SubCommand command) {
        byName.put(command.name(), command);
        for (String alias : command.aliases()) {
            byName.put(alias, command);
        }
        ordered.add(command);
        return this;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        SubCommand sub = byName.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            messages.send(sender, "general.unknown-subcommand",
                    Messages.of("input", args[0]), Messages.of("label", label));
            return true;
        }
        if (!sub.canUse(sender)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        sub.execute(sender, label, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(messages.render("admin.header"));
        for (SubCommand sub : ordered) {
            if (!sub.canUse(sender)) {
                continue;
            }
            sender.sendMessage(messages.render("admin.help-entry",
                    Messages.of("label", label),
                    Messages.of("usage", sub.usage()),
                    Messages.of("description", sub.description())));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (SubCommand sub : ordered) {
                if (sub.canUse(sender) && sub.name().startsWith(prefix)) {
                    names.add(sub.name());
                }
            }
            return names;
        }

        SubCommand sub = byName.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sub.canUse(sender)) {
            return List.of();
        }
        return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}

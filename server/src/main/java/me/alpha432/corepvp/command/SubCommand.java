package me.alpha432.corepvp.command;

import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** One branch of a {@link RootCommand}, e.g. the {@code reload} in /corepvp reload. */
public abstract class SubCommand {

    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final String usage;
    private final String description;
    private final boolean playerOnly;

    protected SubCommand(String name, String permission, String usage, String description,
                         boolean playerOnly, String... aliases) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.permission = permission;
        this.usage = usage;
        this.description = description;
        this.playerOnly = playerOnly;
        this.aliases = Arrays.stream(aliases).map(alias -> alias.toLowerCase(Locale.ROOT)).toList();
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String permission() {
        return permission;
    }

    public String usage() {
        return usage;
    }

    public String description() {
        return description;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    public boolean canUse(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    /** {@code args} excludes the subcommand name itself. */
    public abstract void execute(CommandSender sender, String label, String[] args);

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}

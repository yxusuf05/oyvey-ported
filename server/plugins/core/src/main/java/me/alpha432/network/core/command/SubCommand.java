package me.alpha432.network.core.command;

import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One branch of a {@link BaseCommand}, e.g. the {@code set} in {@code /rank set}. */
public abstract class SubCommand {

    private final String name;
    private final List<String> aliases;
    private String permission;
    private boolean playerOnly;
    private String usage = "";
    private String description = "";

    protected SubCommand(String name, String... aliases) {
        this.name = name.toLowerCase();
        this.aliases = Arrays.stream(aliases).map(String::toLowerCase).toList();
    }

    /** @param args arguments after the sub command name. */
    public abstract void run(CommandSender sender, String[] args);

    public List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    public SubCommand permission(String permission) {
        this.permission = permission;
        return this;
    }

    public SubCommand playerOnly() {
        this.playerOnly = true;
        return this;
    }

    public SubCommand usage(String usage) {
        this.usage = usage;
        return this;
    }

    public SubCommand description(String description) {
        this.description = description;
        return this;
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

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public String usage() {
        return usage;
    }

    public String description() {
        return description;
    }

    public boolean matches(String input) {
        String lower = input.toLowerCase();
        return name.equals(lower) || aliases.contains(lower);
    }
}

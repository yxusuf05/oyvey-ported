package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.arena.ArenaGenerator;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.ffa.FfaArena;
import me.alpha432.corepvp.ffa.FfaMenu;
import me.alpha432.corepvp.kit.Kit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /ffa [join|leave|list|create|delete]} */
public final class FfaCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;

    public FfaCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            new FfaMenu(plugin).open(player);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "leave" -> plugin.ffa().leave(player);
            case "list" -> list(player);
            case "create" -> create(player, args);
            case "delete" -> delete(player, args);
            case "join" -> {
                if (args.length < 2) {
                    messages.send(player, "ffa.join-usage");
                    return;
                }
                join(player, args[1]);
            }
            default -> join(player, args[0]);
        }
    }

    private void join(Player player, String id) {
        FfaArena arena = plugin.ffa().byId(id);
        if (arena == null) {
            messages.send(player, "ffa.not-found", Messages.of("input", id));
            return;
        }
        plugin.ffa().join(player, arena);
    }

    private void list(Player player) {
        List<FfaArena> arenas = plugin.ffa().all();
        if (arenas.isEmpty()) {
            messages.send(player, "ffa.none");
            return;
        }
        player.sendMessage(messages.render("ffa.list-header", Messages.of("count", arenas.size())));
        for (FfaArena arena : arenas) {
            player.sendMessage(messages.render("ffa.list-entry",
                    Messages.of("id", arena.id()),
                    Messages.of("kit", arena.kitId()),
                    Messages.of("players", plugin.ffa().population(arena.id()))));
        }
    }

    /** Generates a platform in the FFA world and registers an arena on it. */
    private void create(Player player, String[] args) {
        if (!player.hasPermission("corepvp.command.admin")) {
            messages.send(player, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(player, "ffa.create-usage",
                    Messages.of("templates", String.join(", ", ArenaGenerator.templateIds())));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.ffa().byId(id) != null) {
            messages.send(player, "ffa.exists", Messages.of("input", id));
            return;
        }
        Kit kit = plugin.kits().byId(args[2]);
        if (kit == null) {
            messages.send(player, "kit.not-found", Messages.of("input", args[2]));
            return;
        }

        String templateId = args.length >= 4 ? args[3] : preferredTemplate(kit);
        ArenaGenerator.Template template = ArenaGenerator.template(templateId);
        if (template == null) {
            messages.send(player, "arena.unknown-template",
                    Messages.of("input", templateId),
                    Messages.of("templates", String.join(", ", ArenaGenerator.templateIds())));
            return;
        }

        World world = plugin.worlds().ffa();
        if (world == null) {
            messages.send(player, "ffa.no-world");
            return;
        }

        messages.send(player, "ffa.creating", Messages.of("id", id));
        plugin.arenaGenerator().generatePlatform(template, world, platform -> {
            plugin.ffa().register(new FfaArena(id, kit.id(), platform.spawn(),
                    plugin.configs().main().getDouble("ffa.safe-radius", 6.0D),
                    platform.bounds(), platform.deathY()));
            messages.send(player, "ffa.created",
                    Messages.of("id", id), Messages.of("kit", kit.displayName()));
        });
    }

    /** A kit's own arena types decide what its FFA platform should look like. */
    private String preferredTemplate(Kit kit) {
        for (String type : kit.arenaTypes()) {
            if (ArenaGenerator.template(type) != null) {
                return type;
            }
        }
        return "flat";
    }

    private void delete(Player player, String[] args) {
        if (!player.hasPermission("corepvp.command.admin")) {
            messages.send(player, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(player, "ffa.delete-usage");
            return;
        }
        if (!plugin.ffa().remove(args[1])) {
            messages.send(player, "ffa.not-found", Messages.of("input", args[1]));
            return;
        }
        messages.send(player, "ffa.deleted", Messages.of("id", args[1].toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(List.of("join", "leave", "list"));
            if (sender.hasPermission("corepvp.command.admin")) {
                options.add("create");
                options.add("delete");
            }
            plugin.ffa().all().forEach(arena -> options.add(arena.id()));
            return options;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("join") || sub.equals("delete"))) {
            return plugin.ffa().all().stream().map(FfaArena::id).toList();
        }
        if (args.length == 3 && sub.equals("create")) {
            return plugin.kits().ids();
        }
        if (args.length == 4 && sub.equals("create")) {
            return ArenaGenerator.templateIds();
        }
        return List.of();
    }
}

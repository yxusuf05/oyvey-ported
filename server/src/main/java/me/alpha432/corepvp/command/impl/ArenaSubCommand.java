package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.arena.ArenaGenerator;
import me.alpha432.corepvp.arena.ArenaManager;
import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.world.WorldService;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /corepvp arena generate|list|tp|delete} */
public final class ArenaSubCommand extends SubCommand {

    private final ArenaManager arenas;
    private final ArenaGenerator generator;
    private final WorldService worlds;
    private final Messages messages;

    public ArenaSubCommand(ArenaManager arenas, ArenaGenerator generator,
                           WorldService worlds, Messages messages) {
        super("arena", "corepvp.command.admin", "arena <generate|list|tp|delete>",
                "Generate and manage arenas.", false);
        this.arenas = arenas;
        this.generator = generator;
        this.worlds = worlds;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "general.usage",
                    Messages.of("label", label), Messages.of("usage", usage()));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "generate" -> generate(sender, args);
            case "list" -> list(sender);
            case "tp" -> teleport(sender, args);
            case "delete" -> delete(sender, args);
            default -> messages.send(sender, "general.usage",
                    Messages.of("label", label), Messages.of("usage", usage()));
        }
    }

    private void generate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "arena.generate-usage",
                    Messages.of("templates", String.join(", ", ArenaGenerator.templateIds())));
            return;
        }

        ArenaGenerator.Template template = ArenaGenerator.template(args[1]);
        if (template == null) {
            messages.send(sender, "arena.unknown-template",
                    Messages.of("input", args[1]),
                    Messages.of("templates", String.join(", ", ArenaGenerator.templateIds())));
            return;
        }

        int count = 1;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                messages.send(sender, "general.invalid-number", Messages.of("input", args[2]));
                return;
            }
        }
        if (count < 1 || count > 64) {
            messages.send(sender, "arena.bad-count");
            return;
        }

        World world = worlds.arenas();
        if (world == null) {
            messages.send(sender, "arena.no-world");
            return;
        }

        int total = count;
        messages.send(sender, "arena.generating",
                Messages.of("count", total), Messages.of("template", template.id()));
        List<String> created = new ArrayList<>();
        generator.generate(template, count, world, created::add,
                () -> messages.send(sender, "arena.generated",
                        Messages.of("count", created.size()),
                        Messages.of("arenas", String.join(", ", created))));
    }

    private void list(CommandSender sender) {
        List<Arena> all = arenas.all();
        if (all.isEmpty()) {
            messages.send(sender, "arena.none");
            return;
        }
        sender.sendMessage(messages.render("arena.list-header", Messages.of("count", all.size())));
        for (Arena arena : all) {
            sender.sendMessage(messages.render("arena.list-entry",
                    Messages.of("id", arena.id()),
                    Messages.of("template", String.valueOf(arena.template())),
                    Messages.of("state", arena.state().name().toLowerCase(Locale.ROOT))));
        }
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "arena.tp-usage");
            return;
        }
        Arena arena = arenas.byId(args[1]);
        if (arena == null || arena.spawns().isEmpty()) {
            messages.send(sender, "arena.not-found", Messages.of("input", args[1]));
            return;
        }
        player.teleport(arena.spawn(0));
        messages.send(sender, "arena.teleported", Messages.of("id", arena.id()));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "arena.delete-usage");
            return;
        }
        if (!arenas.remove(args[1])) {
            messages.send(sender, "arena.not-found", Messages.of("input", args[1]));
            return;
        }
        arenas.saveAll();
        messages.send(sender, "arena.deleted", Messages.of("id", args[1].toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return List.of("generate", "list", "tp", "delete");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("generate")) {
            return ArenaGenerator.templateIds();
        }
        if (args.length == 2 && (sub.equals("tp") || sub.equals("delete"))) {
            return arenas.all().stream().map(Arena::id).toList();
        }
        return List.of();
    }
}

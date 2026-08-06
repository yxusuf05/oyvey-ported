package me.alpha432.network.practice.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.command.SubCommand;
import me.alpha432.network.core.region.Region;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.arena.Arena;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /arena} — build and maintain the duel arenas in game. */
public final class ArenaCommand extends BaseCommand {

    private final Map<UUID, Location> firstCorner = new HashMap<>();
    private final Map<UUID, Location> secondCorner = new HashMap<>();

    public ArenaCommand(PracticePlugin plugin) {
        super(plugin, "arena");
        permission("network.practice.admin");

        sub(new SubCommand("create") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "arena.create-usage");
                    return;
                }
                if (plugin.arenas().get(args[0]).isPresent()) {
                    plugin.messages().send(sender, "arena.exists", "<name>", args[0]);
                    return;
                }
                plugin.arenas().create(args[0]);
                plugin.messages().send(sender, "arena.created", "<name>", args[0].toLowerCase());
            }
        }.usage("<name>").description("Legt eine neue Arena an"));

        sub(new SubCommand("spawn1") {
            @Override
            public void run(CommandSender sender, String[] args) {
                withArena(plugin, sender, args, arena -> {
                    arena.firstSpawn(((Player) sender).getLocation());
                    plugin.arenas().save();
                    plugin.messages().send(sender, "arena.spawn-set",
                            "<name>", arena.name(), "<index>", "1");
                });
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.arenas().names() : List.of();
            }
        }.playerOnly().usage("<name>").description("Setzt den Spawn von Spieler 1"));

        sub(new SubCommand("spawn2") {
            @Override
            public void run(CommandSender sender, String[] args) {
                withArena(plugin, sender, args, arena -> {
                    arena.secondSpawn(((Player) sender).getLocation());
                    plugin.arenas().save();
                    plugin.messages().send(sender, "arena.spawn-set",
                            "<name>", arena.name(), "<index>", "2");
                });
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.arenas().names() : List.of();
            }
        }.playerOnly().usage("<name>").description("Setzt den Spawn von Spieler 2"));

        sub(new SubCommand("pos1") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Location location = ((Player) sender).getLocation();
                firstCorner.put(((Player) sender).getUniqueId(), location);
                plugin.messages().send(sender, "arena.corner-set",
                        "<index>", "1", "<location>", Locations.pretty(location));
            }
        }.playerOnly().description("Markiert die erste Ecke des Kampfbereichs"));

        sub(new SubCommand("pos2") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Location location = ((Player) sender).getLocation();
                secondCorner.put(((Player) sender).getUniqueId(), location);
                plugin.messages().send(sender, "arena.corner-set",
                        "<index>", "2", "<location>", Locations.pretty(location));
            }
        }.playerOnly().description("Markiert die zweite Ecke des Kampfbereichs"));

        sub(new SubCommand("bounds") {
            @Override
            public void run(CommandSender sender, String[] args) {
                withArena(plugin, sender, args, arena -> {
                    UUID id = ((Player) sender).getUniqueId();
                    Location a = firstCorner.get(id);
                    Location b = secondCorner.get(id);
                    if (a == null || b == null) {
                        plugin.messages().send(sender, "arena.no-selection");
                        return;
                    }
                    arena.bounds(Region.between("arena_" + arena.name(), a, b));
                    plugin.arenas().save();
                    plugin.messages().send(sender, "arena.bounds-set", "<name>", arena.name());
                });
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.arenas().names() : List.of();
            }
        }.playerOnly().usage("<name>").description("Übernimmt die markierten Ecken"));

        sub(new SubCommand("kit") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 2) {
                    plugin.messages().send(sender, "arena.kit-usage");
                    return;
                }
                plugin.arenas().get(args[0]).ifPresentOrElse(arena -> {
                    List<String> kits = new ArrayList<>(arena.kits());
                    String kit = args[1].toLowerCase(Locale.ROOT);
                    if (kits.remove(kit)) {
                        plugin.messages().send(sender, "arena.kit-removed",
                                "<name>", arena.name(), "<kit>", kit);
                    } else {
                        kits.add(kit);
                        plugin.messages().send(sender, "arena.kit-added",
                                "<name>", arena.name(), "<kit>", kit);
                    }
                    arena.kits(kits);
                    plugin.arenas().save();
                }, () -> plugin.messages().send(sender, "arena.unknown", "<name>", args[0]));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return plugin.arenas().names();
                }
                return args.length == 2 ? plugin.kits().names() : List.of();
            }
        }.usage("<name> <kit>").description("Schaltet ein Kit für die Arena um"));

        sub(new SubCommand("toggle") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.arenas().get(args.length > 0 ? args[0] : "").ifPresentOrElse(arena -> {
                    arena.enabled(!arena.isEnabled());
                    plugin.arenas().save();
                    plugin.messages().send(sender, arena.isEnabled()
                            ? "arena.enabled" : "arena.disabled", "<name>", arena.name());
                }, () -> plugin.messages().send(sender, "arena.unknown",
                        "<name>", args.length > 0 ? args[0] : "?"));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.arenas().names() : List.of();
            }
        }.usage("<name>").description("Aktiviert oder deaktiviert eine Arena"));

        sub(new SubCommand("delete", "remove") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1 || !plugin.arenas().delete(args[0])) {
                    plugin.messages().send(sender, "arena.unknown",
                            "<name>", args.length > 0 ? args[0] : "?");
                    return;
                }
                plugin.messages().send(sender, "arena.deleted", "<name>", args[0].toLowerCase());
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.arenas().names() : List.of();
            }
        }.usage("<name>").description("Löscht eine Arena"));

        sub(new SubCommand("list") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.messages().send(sender, "arena.list-header",
                        "<count>", String.valueOf(plugin.arenas().all().size()));
                for (Arena arena : plugin.arenas().all()) {
                    plugin.messages().send(sender, "arena.list-line",
                            "<name>", arena.name(),
                            "<state>", plugin.messages().raw(state(arena)),
                            "<kits>", arena.kits().isEmpty()
                                    ? plugin.messages().raw("arena.all-kits")
                                    : String.join(", ", arena.kits()));
                }
            }
        }.description("Listet alle Arenen"));

        sub(new SubCommand("teleport", "tp") {
            @Override
            public void run(CommandSender sender, String[] args) {
                withArena(plugin, sender, args, arena -> {
                    if (arena.firstSpawn() == null) {
                        plugin.messages().send(sender, "arena.incomplete", "<name>", arena.name());
                        return;
                    }
                    ((Player) sender).teleport(arena.firstSpawn());
                    plugin.messages().send(sender, "arena.teleported", "<name>", arena.name());
                });
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.arenas().names() : List.of();
            }
        }.playerOnly().usage("<name>").description("Teleportiert dich in eine Arena"));

        sub(new SubCommand("sethub") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player player = (Player) sender;
                if (!plugin.hub().isPractice(player)) {
                    plugin.messages().send(sender, "arena.wrong-world",
                            "<world>", plugin.hub().worldName());
                    return;
                }
                plugin.hub().spawn(player.getLocation());
                plugin.messages().send(sender, "arena.hub-set",
                        "<location>", Locations.pretty(player.getLocation()));
            }
        }.playerOnly().description("Setzt den Practice-Hub auf deine Position"));

        sub(new SubCommand("setffa") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "arena.setffa-usage");
                    return;
                }
                plugin.ffa().setSpawn(args[0], ((Player) sender).getLocation());
                plugin.messages().send(sender, "arena.ffa-set", "<name>", args[0].toLowerCase());
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.ffa().names() : List.of();
            }
        }.playerOnly().usage("<name>").description("Setzt den Spawn einer FFA-Arena"));

        sub(new SubCommand("reload") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.reloadEverything();
                plugin.messages().send(sender, "arena.reloaded");
            }
        }.description("Lädt Kits, Arenen und FFA neu"));
    }

    private static String state(Arena arena) {
        if (!arena.isComplete()) {
            return "arena.state-incomplete";
        }
        if (!arena.isEnabled()) {
            return "arena.state-disabled";
        }
        return arena.isOccupied() ? "arena.state-occupied" : "arena.state-free";
    }

    private static void withArena(PracticePlugin plugin, CommandSender sender, String[] args,
                                  java.util.function.Consumer<Arena> action) {
        if (args.length < 1) {
            plugin.messages().send(sender, "arena.name-missing");
            return;
        }
        plugin.arenas().get(args[0]).ifPresentOrElse(action,
                () -> plugin.messages().send(sender, "arena.unknown", "<name>", args[0]));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? CommandUtil.filter(args.length == 0 ? "" : args[0],
                List.of("create", "spawn1", "spawn2", "pos1", "pos2", "bounds", "kit",
                        "toggle", "delete", "list", "teleport", "sethub", "setffa", "reload"))
                : List.of();
    }
}

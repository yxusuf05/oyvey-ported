package me.alpha432.network.core.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.CorePlugin;
import me.alpha432.network.core.region.Region;
import me.alpha432.network.core.region.RegionFlag;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /region} — build the protected cuboids of the network in game. */
public final class RegionCommand extends BaseCommand {

    private final Map<UUID, Location> firstCorner = new HashMap<>();
    private final Map<UUID, Location> secondCorner = new HashMap<>();

    public RegionCommand(CorePlugin plugin) {
        super(plugin, "region");
        permission("network.command.region");

        sub(new SubCommand("pos1", "1") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Location location = player(sender).getLocation();
                firstCorner.put(player(sender).getUniqueId(), location);
                Core.messages().send(sender, "region.pos-set",
                        "<index>", "1", "<location>", Locations.pretty(location));
            }
        }.playerOnly().description("Marks the first corner"));

        sub(new SubCommand("pos2", "2") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Location location = player(sender).getLocation();
                secondCorner.put(player(sender).getUniqueId(), location);
                Core.messages().send(sender, "region.pos-set",
                        "<index>", "2", "<location>", Locations.pretty(location));
            }
        }.playerOnly().description("Marks the second corner"));

        sub(new SubCommand("create", "add") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    Core.messages().send(sender, "region.create-usage");
                    return;
                }
                UUID id = player(sender).getUniqueId();
                Location a = firstCorner.get(id);
                Location b = secondCorner.get(id);
                if (a == null || b == null) {
                    Core.messages().send(sender, "region.no-selection");
                    return;
                }
                if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
                    Core.messages().send(sender, "region.different-worlds");
                    return;
                }
                if (Core.regions().get(args[0]).isPresent()) {
                    Core.messages().send(sender, "region.exists", "<name>", args[0]);
                    return;
                }
                Region region = Region.between(args[0], a, b);
                if (args.length > 1) {
                    region.priority(CommandUtil.parseInt(args[1], 0));
                }
                Core.regions().add(region);
                Core.messages().send(sender, "region.created",
                        "<name>", region.name(), "<volume>", String.valueOf(region.volume()));
            }
        }.playerOnly().usage("<name> [priority]").description("Creates a region from the selection"));

        sub(new SubCommand("delete", "remove") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    Core.messages().send(sender, "region.delete-usage");
                    return;
                }
                if (Core.regions().remove(args[0])) {
                    Core.messages().send(sender, "region.deleted", "<name>", args[0]);
                } else {
                    Core.messages().send(sender, "region.unknown", "<name>", args[0]);
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? regionNames() : List.of();
            }
        }.usage("<name>").description("Deletes a region"));

        sub(new SubCommand("flag") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 3) {
                    Core.messages().send(sender, "region.flag-usage");
                    return;
                }
                Region region = Core.regions().get(args[0]).orElse(null);
                if (region == null) {
                    Core.messages().send(sender, "region.unknown", "<name>", args[0]);
                    return;
                }
                RegionFlag flag = RegionFlag.byKey(args[1]);
                if (flag == null) {
                    Core.messages().send(sender, "region.unknown-flag", "<flag>", args[1]);
                    return;
                }
                if (args[2].equalsIgnoreCase("unset")) {
                    region.clearFlag(flag);
                    Core.regions().save();
                    Core.messages().send(sender, "region.flag-unset",
                            "<name>", region.name(), "<flag>", flag.key());
                    return;
                }
                boolean value = Boolean.parseBoolean(args[2]);
                region.flag(flag, value);
                Core.regions().save();
                Core.messages().send(sender, "region.flag-set",
                        "<name>", region.name(), "<flag>", flag.key(), "<value>", String.valueOf(value));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return regionNames();
                }
                if (args.length == 2) {
                    List<String> flags = new ArrayList<>();
                    for (RegionFlag flag : RegionFlag.values()) {
                        flags.add(flag.key());
                    }
                    return flags;
                }
                if (args.length == 3) {
                    return List.of("true", "false", "unset");
                }
                return List.of();
            }
        }.usage("<name> <flag> <true|false|unset>").description("Changes a region flag"));

        sub(new SubCommand("priority") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 2) {
                    Core.messages().send(sender, "region.priority-usage");
                    return;
                }
                Region region = Core.regions().get(args[0]).orElse(null);
                if (region == null) {
                    Core.messages().send(sender, "region.unknown", "<name>", args[0]);
                    return;
                }
                region.priority(CommandUtil.parseInt(args[1], 0));
                Core.regions().save();
                Core.messages().send(sender, "region.priority-set",
                        "<name>", region.name(), "<priority>", String.valueOf(region.priority()));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? regionNames() : List.of();
            }
        }.usage("<name> <priority>").description("Changes the priority of a region"));

        sub(new SubCommand("list") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Core.messages().send(sender, "region.list-header",
                        "<count>", String.valueOf(Core.regions().all().size()));
                for (Region region : Core.regions().all()) {
                    Core.messages().send(sender, "region.list-line",
                            "<name>", region.name(),
                            "<world>", region.world(),
                            "<priority>", String.valueOf(region.priority()),
                            "<flags>", String.valueOf(region.flags().size()));
                }
            }
        }.description("Lists every region"));

        sub(new SubCommand("here") {
            @Override
            public void run(CommandSender sender, String[] args) {
                List<Region> here = Core.regions().at(player(sender).getLocation());
                if (here.isEmpty()) {
                    Core.messages().send(sender, "region.none-here");
                    return;
                }
                Core.messages().send(sender, "region.list-header", "<count>", String.valueOf(here.size()));
                for (Region region : here) {
                    Core.messages().send(sender, "region.list-line",
                            "<name>", region.name(),
                            "<world>", region.world(),
                            "<priority>", String.valueOf(region.priority()),
                            "<flags>", String.valueOf(region.flags().size()));
                }
            }
        }.playerOnly().description("Lists the regions you are standing in"));
    }

    private static List<String> regionNames() {
        List<String> names = new ArrayList<>();
        Core.regions().all().forEach(region -> names.add(region.name()));
        return names;
    }
}

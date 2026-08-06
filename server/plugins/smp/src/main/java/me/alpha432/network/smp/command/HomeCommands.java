package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** {@code /home}, {@code /sethome}, {@code /delhome} and {@code /homes}. */
public final class HomeCommands {

    private HomeCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new HomeCommand(plugin).register();
        new SetHomeCommand(plugin).register();
        new DelHomeCommand(plugin).register();
        new HomesCommand(plugin).register();
    }

    private static final class HomeCommand extends BaseCommand {

        private final SmpPlugin smp;

        private HomeCommand(SmpPlugin plugin) {
            super(plugin, "home");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            Map<String, Location> homes = smp.homes().homes(player);
            if (homes.isEmpty()) {
                smp.messages().send(sender, "home.none");
                return;
            }
            String name = args.length > 0 ? args[0] : homes.keySet().iterator().next();
            Location target = smp.homes().home(player, name);
            if (target == null) {
                smp.messages().send(sender, "home.unknown", "<name>", name);
                return;
            }
            if (!smp.startTeleport(player, "home")) {
                return;
            }
            smp.messages().send(sender, "home.teleporting", "<name>", name);
            Core.teleports().teleportWithWarmup(player, target, smp.warmupFor(player));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player player && args.length <= 1
                    ? smp.homes().names(player) : List.of();
        }
    }

    private static final class SetHomeCommand extends BaseCommand {

        private final SmpPlugin smp;

        private SetHomeCommand(SmpPlugin plugin) {
            super(plugin, "sethome");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (!smp.spawn().isSmp(player)) {
                smp.messages().send(sender, "home.wrong-world");
                return;
            }
            String name = args.length > 0 ? args[0] : "home";
            if (!name.matches("[A-Za-z0-9_-]{1,16}")) {
                smp.messages().send(sender, "home.invalid-name");
                return;
            }
            if (!smp.homes().set(player, name, player.getLocation())) {
                smp.messages().send(sender, "home.limit-reached",
                        "<limit>", String.valueOf(smp.homes().limit(player)));
                return;
            }
            smp.messages().send(sender, "home.set",
                    "<name>", name.toLowerCase(),
                    "<location>", Locations.pretty(player.getLocation()),
                    "<used>", smp.homes().usage(player));
        }
    }

    private static final class DelHomeCommand extends BaseCommand {

        private final SmpPlugin smp;

        private DelHomeCommand(SmpPlugin plugin) {
            super(plugin, "delhome");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length == 0) {
                smp.messages().send(sender, "home.delete-usage");
                return;
            }
            if (!smp.homes().delete(player, args[0])) {
                smp.messages().send(sender, "home.unknown", "<name>", args[0]);
                return;
            }
            smp.messages().send(sender, "home.deleted", "<name>", args[0].toLowerCase());
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player player && args.length <= 1
                    ? smp.homes().names(player) : List.of();
        }
    }

    private static final class HomesCommand extends BaseCommand {

        private final SmpPlugin smp;

        private HomesCommand(SmpPlugin plugin) {
            super(plugin, "homes");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            Map<String, Location> homes = smp.homes().homes(player);
            if (homes.isEmpty()) {
                smp.messages().send(sender, "home.none");
                return;
            }
            smp.messages().send(sender, "home.list-header", "<used>", smp.homes().usage(player));
            for (Map.Entry<String, Location> entry : homes.entrySet()) {
                smp.messages().send(sender, "home.list-line",
                        "<name>", entry.getKey(),
                        "<location>", Locations.pretty(entry.getValue()));
            }
        }
    }
}

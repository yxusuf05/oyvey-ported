package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.teleport.TeleportRequestService;
import me.alpha432.network.smp.teleport.TeleportRequestService.Direction;
import me.alpha432.network.smp.teleport.TeleportRequestService.Request;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** {@code /tpa}, {@code /tpahere}, {@code /tpaccept}, {@code /tpdeny}, {@code /back}, {@code /rtp}. */
public final class TeleportCommands {

    private TeleportCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new RequestCommand(plugin, "tpa", Direction.TO_TARGET).register();
        new RequestCommand(plugin, "tpahere", Direction.TO_SENDER).register();
        new AcceptCommand(plugin).register();
        new DenyCommand(plugin).register();
        new BackCommand(plugin).register();
        new RtpCommand(plugin).register();
    }

    /** Both /tpa and /tpahere; only the direction differs. */
    private static final class RequestCommand extends BaseCommand {

        private final SmpPlugin smp;
        private final Direction direction;

        private RequestCommand(SmpPlugin plugin, String name, Direction direction) {
            super(plugin, name);
            this.smp = plugin;
            this.direction = direction;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length == 0) {
                smp.messages().send(sender, "tpa.usage");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !player.canSee(target)) {
                smp.messages().send(sender, "tpa.not-found", "<name>", args[0]);
                return;
            }
            if (target.equals(player)) {
                smp.messages().send(sender, "tpa.self");
                return;
            }
            if (!smp.requests().add(player, target, direction)) {
                smp.messages().send(sender, "tpa.already-pending", "<player>", target.getName());
                return;
            }
            smp.messages().send(sender, "tpa.sent", "<player>", target.getName());
            smp.messages().send(target,
                    direction == Direction.TO_TARGET ? "tpa.received" : "tpa.received-here",
                    "<player>", player.getName());
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }

    private static final class AcceptCommand extends BaseCommand {

        private final SmpPlugin smp;

        private AcceptCommand(SmpPlugin plugin) {
            super(plugin, "tpaccept");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player target = player(sender);
            Request request = smp.requests().take(target, args.length > 0 ? args[0] : null);
            if (request == null) {
                smp.messages().send(sender, "tpa.nothing-pending");
                return;
            }
            Player requester = Bukkit.getPlayer(request.sender());
            if (requester == null) {
                smp.messages().send(sender, "tpa.sender-offline");
                return;
            }
            Player traveller = request.direction() == Direction.TO_TARGET ? requester : target;
            Location destination = request.direction() == Direction.TO_TARGET
                    ? target.getLocation() : requester.getLocation();

            smp.messages().send(requester, "tpa.accepted-sender", "<player>", target.getName());
            smp.messages().send(target, "tpa.accepted-target", "<player>", requester.getName());
            Core.teleports().teleportWithWarmup(traveller, destination, smp.warmupFor(traveller));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length > 1 || !(sender instanceof Player player)) {
                return List.of();
            }
            return smp.requests().pending(player).stream()
                    .map(request -> Bukkit.getOfflinePlayer(request.sender()).getName())
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }

    private static final class DenyCommand extends BaseCommand {

        private final SmpPlugin smp;

        private DenyCommand(SmpPlugin plugin) {
            super(plugin, "tpdeny");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player target = player(sender);
            TeleportRequestService.Request request =
                    smp.requests().take(target, args.length > 0 ? args[0] : null);
            if (request == null) {
                smp.messages().send(sender, "tpa.nothing-pending");
                return;
            }
            smp.messages().send(sender, "tpa.denied-target");
            Player requester = Bukkit.getPlayer(request.sender());
            if (requester != null) {
                smp.messages().send(requester, "tpa.denied-sender", "<player>", target.getName());
            }
        }
    }

    private static final class BackCommand extends BaseCommand {

        private final SmpPlugin smp;

        private BackCommand(SmpPlugin plugin) {
            super(plugin, "back");
            this.smp = plugin;
            permission("network.smp.back");
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            Core.teleports().popBack(player).ifPresentOrElse(location -> {
                if (!smp.startTeleport(player, "back")) {
                    // Put the position back so the cooldown does not eat it.
                    Core.teleports().pushBack(player, location);
                    return;
                }
                smp.messages().send(sender, "back.teleporting",
                        "<location>", Locations.pretty(location));
                Core.teleports().teleportWithWarmup(player, location, smp.warmupFor(player));
            }, () -> smp.messages().send(sender, "back.nothing"));
        }
    }

    private static final class RtpCommand extends BaseCommand {

        private final SmpPlugin smp;

        private RtpCommand(SmpPlugin plugin) {
            super(plugin, "rtp");
            this.smp = plugin;
            permission("network.smp.rtp");
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (!smp.spawn().isSmp(player)) {
                smp.messages().send(sender, "rtp.wrong-world");
                return;
            }
            if (!smp.startTeleport(player, "rtp")) {
                return;
            }
            World world = player.getWorld();
            int radius = smp.getConfig().getInt("rtp.radius", 5000);
            int minRadius = smp.getConfig().getInt("rtp.min-radius", 500);
            Location candidate = randomLocation(world, minRadius, radius);

            smp.messages().send(sender, "rtp.searching");
            // Load the chunk first so the highest block is known before teleporting.
            world.getChunkAtAsync(candidate).thenAccept(chunk -> {
                Location target = world.getHighestBlockAt(candidate).getLocation().add(0.5, 1, 0.5);
                Core.teleports().teleport(player, Locations.findSafe(target));
                smp.messages().send(sender, "rtp.done", "<location>", Locations.pretty(target));
            });
        }

        private static Location randomLocation(World world, int minRadius, int maxRadius) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int distance = random.nextInt(minRadius, Math.max(minRadius + 1, maxRadius));
            double angle = random.nextDouble() * Math.PI * 2;
            int x = (int) (Math.cos(angle) * distance);
            int z = (int) (Math.sin(angle) * distance);
            return new Location(world, x, world.getSeaLevel(), z);
        }
    }
}

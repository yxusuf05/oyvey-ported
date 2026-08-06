package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.warp.Warp;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /warp}, {@code /setwarp}, {@code /delwarp} and {@code /warps}. */
public final class WarpCommands {

    private WarpCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new WarpCommand(plugin).register();
        new SetWarpCommand(plugin).register();
        new DelWarpCommand(plugin).register();
        new WarpsCommand(plugin).register();
    }

    private static void teleport(SmpPlugin smp, Player player, Warp warp) {
        if (warp.permission() != null && !player.hasPermission(warp.permission())) {
            smp.messages().send(player, "warp.no-permission", "<name>", warp.name());
            return;
        }
        if (!smp.startTeleport(player, "warp")) {
            return;
        }
        smp.messages().send(player, "warp.teleporting", "<name>", warp.name());
        Core.teleports().teleportWithWarmup(player, warp.location(), smp.warmupFor(player));
    }

    private static final class WarpCommand extends BaseCommand {

        private final SmpPlugin smp;

        private WarpCommand(SmpPlugin plugin) {
            super(plugin, "warp");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length == 0) {
                new WarpMenu(smp, player).open(player);
                return;
            }
            smp.warps().get(args[0]).ifPresentOrElse(
                    warp -> teleport(smp, player, warp),
                    () -> smp.messages().send(sender, "warp.unknown", "<name>", args[0]));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? smp.warps().names() : List.of();
        }
    }

    private static final class SetWarpCommand extends BaseCommand {

        private final SmpPlugin smp;

        private SetWarpCommand(SmpPlugin plugin) {
            super(plugin, "setwarp");
            this.smp = plugin;
            permission("network.smp.admin");
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            if (args.length == 0) {
                smp.messages().send(sender, "warp.set-usage");
                return;
            }
            Player player = player(sender);
            smp.warps().set(args[0], player.getLocation());
            smp.messages().send(sender, "warp.set",
                    "<name>", args[0], "<location>", Locations.pretty(player.getLocation()));
        }
    }

    private static final class DelWarpCommand extends BaseCommand {

        private final SmpPlugin smp;

        private DelWarpCommand(SmpPlugin plugin) {
            super(plugin, "delwarp");
            this.smp = plugin;
            permission("network.smp.admin");
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            if (args.length == 0) {
                smp.messages().send(sender, "warp.delete-usage");
                return;
            }
            if (!smp.warps().delete(args[0])) {
                smp.messages().send(sender, "warp.unknown", "<name>", args[0]);
                return;
            }
            smp.messages().send(sender, "warp.deleted", "<name>", args[0]);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? smp.warps().names() : List.of();
        }
    }

    private static final class WarpsCommand extends BaseCommand {

        private final SmpPlugin smp;

        private WarpsCommand(SmpPlugin plugin) {
            super(plugin, "warps");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            new WarpMenu(smp, player).open(player);
        }
    }

    /** Paged list of every warp the viewer may use. */
    private static final class WarpMenu extends PagedMenu {

        private WarpMenu(SmpPlugin plugin, Player viewer) {
            super(plugin.messages().get("warp.menu-title"), 5);
            List<MenuItem> items = new ArrayList<>();
            for (Warp warp : plugin.warps().visibleTo(viewer)) {
                List<String> lore = new ArrayList<>();
                if (!warp.description().isBlank()) {
                    lore.add(warp.description());
                }
                lore.add(plugin.messages().raw("warp.menu-location")
                        .replace("<location>", Locations.pretty(warp.location())));
                lore.add(plugin.messages().raw("warp.menu-click"));
                items.add(MenuItem.of(
                        ItemBuilder.of(warp.icon()).name("<yellow>" + warp.name()).lore(lore)
                                .hideAttributes().build(),
                        event -> {
                            viewer.closeInventory();
                            teleport(plugin, viewer, warp);
                        }));
            }
            content(items);
        }
    }
}

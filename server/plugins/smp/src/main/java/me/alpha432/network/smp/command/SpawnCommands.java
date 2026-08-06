package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /spawn} and {@code /setspawn}. */
public final class SpawnCommands {

    private SpawnCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new SpawnCommand(plugin).register();
        new SetSpawnCommand(plugin).register();
    }

    private static final class SpawnCommand extends BaseCommand {

        private final SmpPlugin smp;

        private SpawnCommand(SmpPlugin plugin) {
            super(plugin, "spawn");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            Location spawn = smp.spawn().spawn();
            if (spawn == null) {
                smp.messages().send(sender, "spawn.missing");
                return;
            }
            if (!smp.startTeleport(player, "spawn")) {
                return;
            }
            Core.teleports().teleportWithWarmup(player, spawn, smp.warmupFor(player));
        }
    }

    private static final class SetSpawnCommand extends BaseCommand {

        private final SmpPlugin smp;

        private SetSpawnCommand(SmpPlugin plugin) {
            super(plugin, "setspawn");
            this.smp = plugin;
            permission("network.smp.admin");
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (!smp.spawn().isSmp(player)) {
                smp.messages().send(sender, "spawn.wrong-world", "<world>", smp.spawn().worldName());
                return;
            }
            smp.spawn().spawn(player.getLocation());
            smp.messages().send(sender, "spawn.set", "<location>", Locations.pretty(player.getLocation()));
        }
    }
}

package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.SubCommand;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.fight.FightArea;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /fightarea} — set up and manage the PvP areas at the SMP spawn. */
public final class FightAreaCommand extends BaseCommand {

    public FightAreaCommand(SmpPlugin plugin) {
        super(plugin, "fightarea");

        sub(new SubCommand("list") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.messages().send(sender, "fightarea.list-header",
                        "<count>", String.valueOf(plugin.fightAreas().all().size()));
                for (FightArea area : plugin.fightAreas().all()) {
                    plugin.messages().send(sender, "fightarea.list-line",
                            "<name>", area.name(),
                            "<type>", area.type().name().toLowerCase(),
                            "<reset>", area.resetSeconds() <= 0
                                    ? plugin.messages().raw("fightarea.no-reset")
                                    : area.secondsUntilReset() + "s",
                            "<spawn>", area.spawn() == null
                                    ? plugin.messages().raw("fightarea.no-spawn")
                                    : Locations.pretty(area.spawn()));
                }
            }
        }.description("Listet die Kampfbereiche"));

        sub(new SubCommand("join", "tp") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "fightarea.join-usage");
                    return;
                }
                Player player = (Player) sender;
                plugin.fightAreas().get(args[0]).ifPresentOrElse(area -> {
                    if (area.spawn() == null) {
                        plugin.messages().send(sender, "fightarea.spawn-missing", "<name>", area.name());
                        return;
                    }
                    Core.teleports().teleport(player, area.spawn());
                    plugin.messages().send(sender, "fightarea.joined", "<name>", area.name());
                }, () -> plugin.messages().send(sender, "fightarea.unknown", "<name>", args[0]));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.fightAreas().names() : List.of();
            }
        }.playerOnly().usage("<name>").description("Teleportiert dich in einen Kampfbereich"));

        sub(new SubCommand("bounds") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.messages().send(sender, "fightarea.bounds-hint");
            }
        }.description("Erklärt, wie die Grenzen gesetzt werden"));

        sub(new SubCommand("setspawn") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "fightarea.setspawn-usage");
                    return;
                }
                Player player = (Player) sender;
                plugin.fightAreas().get(args[0]).ifPresentOrElse(area -> {
                    area.spawn(player.getLocation());
                    plugin.fightAreas().save();
                    plugin.messages().send(sender, "fightarea.spawn-set",
                            "<name>", area.name(),
                            "<location>", Locations.pretty(player.getLocation()));
                }, () -> plugin.messages().send(sender, "fightarea.unknown", "<name>", args[0]));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.fightAreas().names() : List.of();
            }
        }.playerOnly().permission("network.smp.admin").usage("<name>")
                .description("Setzt den Einstiegspunkt"));

        sub(new SubCommand("reset") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "fightarea.reset-usage");
                    return;
                }
                plugin.fightAreas().get(args[0]).ifPresentOrElse(area -> {
                    int restored = area.reset();
                    plugin.messages().send(sender, "fightarea.reset-done",
                            "<name>", area.name(), "<blocks>", String.valueOf(restored));
                }, () -> plugin.messages().send(sender, "fightarea.unknown", "<name>", args[0]));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.fightAreas().names() : List.of();
            }
        }.permission("network.smp.admin").usage("<name>").description("Setzt einen Bereich sofort zurück"));

        sub(new SubCommand("reload") {
            @Override
            public void run(CommandSender sender, String[] args) {
                plugin.fightAreas().resetAll();
                plugin.fightAreas().reload();
                plugin.messages().send(sender, "fightarea.reloaded",
                        "<count>", String.valueOf(plugin.fightAreas().all().size()));
            }
        }.permission("network.smp.admin").description("Lädt fightareas.yml neu"));
    }
}

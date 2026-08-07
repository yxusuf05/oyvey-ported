package me.alpha432.network.smp.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.command.SubCommand;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.crate.Crate;
import me.alpha432.network.smp.crate.CratePreviewMenu;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /crate} for setting crates up and {@code /key} for handing out keys.
 *
 * <p>{@code /key give} and {@code /key giveall} are what a web shop calls through the console
 * after a purchase — the plugin itself never touches a payment.
 */
public final class CrateCommands {

    private CrateCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new CrateCommand(plugin).register();
        new KeyCommand(plugin).register();
    }

    private static final class CrateCommand extends BaseCommand {

        private CrateCommand(SmpPlugin plugin) {
            super(plugin, "crate");

            sub(new SubCommand("preview", "list") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    Player player = (Player) sender;
                    if (args.length == 0) {
                        plugin.messages().send(sender, "crate.list-header",
                                "<count>", String.valueOf(plugin.crates().all().size()));
                        for (Crate crate : plugin.crates().all()) {
                            plugin.messages().send(sender, "crate.list-line",
                                    "<id>", crate.id(),
                                    "<name>", crate.displayName(),
                                    "<rewards>", String.valueOf(crate.rewards().size()),
                                    "<keys>", String.valueOf(plugin.crates().countKeys(player, crate)),
                                    "<block>", crate.block() == null
                                            ? plugin.messages().raw("crate.no-block")
                                            : Locations.pretty(crate.block()));
                        }
                        return;
                    }
                    plugin.crates().get(args[0]).ifPresentOrElse(
                            crate -> new CratePreviewMenu(plugin, player, crate).open(player),
                            () -> plugin.messages().send(sender, "crate.unknown", "<name>", args[0]));
                }

                @Override
                public List<String> complete(CommandSender sender, String[] args) {
                    return args.length == 1 ? plugin.crates().names() : List.of();
                }
            }.playerOnly().usage("[crate]").description("Zeigt die Gewinne einer Crate"));

            sub(new SubCommand("setblock") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    if (args.length < 1) {
                        plugin.messages().send(sender, "crate.setblock-usage");
                        return;
                    }
                    Player player = (Player) sender;
                    Block target = player.getTargetBlockExact(6);
                    if (target == null) {
                        plugin.messages().send(sender, "crate.no-target-block");
                        return;
                    }
                    plugin.crates().get(args[0]).ifPresentOrElse(crate -> {
                        crate.block(target.getLocation());
                        plugin.crates().saveBlocks();
                        plugin.messages().send(sender, "crate.block-set",
                                "<name>", crate.displayName(),
                                "<location>", Locations.pretty(target.getLocation()));
                    }, () -> plugin.messages().send(sender, "crate.unknown", "<name>", args[0]));
                }

                @Override
                public List<String> complete(CommandSender sender, String[] args) {
                    return args.length == 1 ? plugin.crates().names() : List.of();
                }
            }.playerOnly().permission("network.smp.admin").usage("<crate>")
                    .description("Setzt die Crate auf den Block, den du ansiehst"));

            sub(new SubCommand("unsetblock") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    if (args.length < 1) {
                        plugin.messages().send(sender, "crate.setblock-usage");
                        return;
                    }
                    plugin.crates().get(args[0]).ifPresentOrElse(crate -> {
                        crate.block(null);
                        plugin.crates().saveBlocks();
                        plugin.messages().send(sender, "crate.block-unset", "<name>", crate.displayName());
                    }, () -> plugin.messages().send(sender, "crate.unknown", "<name>", args[0]));
                }

                @Override
                public List<String> complete(CommandSender sender, String[] args) {
                    return args.length == 1 ? plugin.crates().names() : List.of();
                }
            }.permission("network.smp.admin").usage("<crate>").description("Entfernt den Crate-Block"));

            sub(new SubCommand("reload") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    plugin.crates().reload();
                    plugin.messages().send(sender, "crate.reloaded",
                            "<count>", String.valueOf(plugin.crates().all().size()));
                }
            }.permission("network.smp.admin").description("Lädt crates.yml neu"));
        }
    }

    /** {@code /key give|giveall|check} — the interface a web shop drives. */
    private static final class KeyCommand extends BaseCommand {

        private KeyCommand(SmpPlugin plugin) {
            super(plugin, "key");

            sub(new SubCommand("check", "list") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    Player player = (Player) sender;
                    plugin.messages().send(sender, "key.check-header");
                    for (Crate crate : plugin.crates().all()) {
                        plugin.messages().send(sender, "key.check-line",
                                "<crate>", crate.displayName(),
                                "<count>", String.valueOf(plugin.crates().countKeys(player, crate)));
                    }
                }
            }.playerOnly().description("Zeigt, wie viele Keys du hast"));

            sub(new SubCommand("give") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    if (args.length < 2) {
                        plugin.messages().send(sender, "key.give-usage");
                        return;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        plugin.messages().send(sender, "key.player-offline", "<name>", args[0]);
                        return;
                    }
                    Crate crate = plugin.crates().get(args[1]).orElse(null);
                    if (crate == null) {
                        plugin.messages().send(sender, "crate.unknown", "<name>", args[1]);
                        return;
                    }
                    int amount = args.length > 2 ? Math.max(1, CommandUtil.parseInt(args[2], 1)) : 1;
                    giveKeys(plugin, target, crate, amount);
                    plugin.messages().send(sender, "key.given",
                            "<amount>", String.valueOf(amount),
                            "<crate>", crate.displayName(),
                            "<player>", target.getName());
                }

                @Override
                public List<String> complete(CommandSender sender, String[] args) {
                    if (args.length == 1) {
                        return CommandUtil.onlineNames(sender);
                    }
                    return args.length == 2 ? plugin.crates().names() : List.of();
                }
            }.permission("network.smp.keys").usage("<spieler> <crate> [anzahl]")
                    .description("Gibt einem Spieler Keys"));

            sub(new SubCommand("giveall") {
                @Override
                public void run(CommandSender sender, String[] args) {
                    if (args.length < 1) {
                        plugin.messages().send(sender, "key.giveall-usage");
                        return;
                    }
                    Crate crate = plugin.crates().get(args[0]).orElse(null);
                    if (crate == null) {
                        plugin.messages().send(sender, "crate.unknown", "<name>", args[0]);
                        return;
                    }
                    int amount = args.length > 1 ? Math.max(1, CommandUtil.parseInt(args[1], 1)) : 1;
                    int players = 0;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        giveKeys(plugin, online, crate, amount);
                        players++;
                    }
                    Bukkit.broadcast(plugin.messages().get("key.giveall-broadcast",
                            "<amount>", String.valueOf(amount),
                            "<crate>", crate.displayName()));
                    plugin.messages().send(sender, "key.giveall-done",
                            "<amount>", String.valueOf(amount),
                            "<crate>", crate.displayName(),
                            "<players>", String.valueOf(players));
                }

                @Override
                public List<String> complete(CommandSender sender, String[] args) {
                    return args.length == 1 ? plugin.crates().names() : List.of();
                }
            }.permission("network.smp.keys").usage("<crate> [anzahl]")
                    .description("Gibt allen Online-Spielern Keys"));
        }
    }

    /** Splits the amount into stacks and drops what does not fit. */
    private static void giveKeys(SmpPlugin plugin, Player player, Crate crate, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack keys = plugin.crates().key(crate, remaining);
            remaining -= keys.getAmount();
            player.getInventory().addItem(keys).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        plugin.messages().send(player, "key.received",
                "<amount>", String.valueOf(amount), "<crate>", crate.displayName());
    }
}

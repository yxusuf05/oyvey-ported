package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.command.SubCommand;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

/** {@code /balance}, {@code /pay}, {@code /baltop} and the admin {@code /eco}. */
public final class EconomyCommands {

    private EconomyCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new BalanceCommand(plugin).register();
        new PayCommand(plugin).register();
        new BalTopCommand(plugin).register();
        new EcoCommand(plugin).register();
    }

    private static final class BalanceCommand extends BaseCommand {

        private final SmpPlugin smp;

        private BalanceCommand(SmpPlugin plugin) {
            super(plugin, "balance");
            this.smp = plugin;
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    Core.messages().send(sender, "error.player-only");
                    return;
                }
                smp.messages().send(sender, "economy.balance-self",
                        "<balance>", Core.economy().format(Core.economy().balance(player)));
                return;
            }
            Core.profiles().lookup(args[0]).thenAccept(profile ->
                    Bukkit.getScheduler().runTask(smp, () -> {
                        if (profile == null) {
                            smp.messages().send(sender, "economy.unknown-player", "<name>", args[0]);
                            return;
                        }
                        smp.messages().send(sender, "economy.balance-other",
                                "<player>", profile.name(),
                                "<balance>", Core.economy().format(profile.balance()));
                    }));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }

    private static final class PayCommand extends BaseCommand {

        private final SmpPlugin smp;

        private PayCommand(SmpPlugin plugin) {
            super(plugin, "pay");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length < 2) {
                smp.messages().send(sender, "economy.pay-usage");
                return;
            }
            double amount = CommandUtil.parseDouble(args[1], -1);
            if (amount <= 0) {
                Core.messages().send(sender, "error.invalid-number", "<input>", args[1]);
                return;
            }
            double minimum = smp.getConfig().getDouble("economy.minimum-payment", 1.0D);
            if (amount < minimum) {
                smp.messages().send(sender, "economy.pay-too-small",
                        "<minimum>", Core.economy().format(minimum));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                smp.messages().send(sender, "economy.target-offline", "<name>", args[0]);
                return;
            }
            if (target.equals(player)) {
                smp.messages().send(sender, "economy.pay-self");
                return;
            }
            PlayerProfile from = Core.profiles().get(player);
            PlayerProfile to = Core.profiles().get(target);
            if (!Core.economy().transfer(from, to, amount)) {
                smp.messages().send(sender, "economy.not-enough",
                        "<amount>", Core.economy().format(amount));
                return;
            }
            smp.messages().send(sender, "economy.pay-sent",
                    "<amount>", Core.economy().format(amount), "<player>", target.getName());
            smp.messages().send(target, "economy.pay-received",
                    "<amount>", Core.economy().format(amount), "<player>", player.getName());
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }

    private static final class BalTopCommand extends BaseCommand {

        private final SmpPlugin smp;

        private BalTopCommand(SmpPlugin plugin) {
            super(plugin, "baltop");
            this.smp = plugin;
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            int limit = smp.getConfig().getInt("economy.baltop-size", 10);
            Core.profiles().topBalances(limit).thenAccept(profiles ->
                    Bukkit.getScheduler().runTask(smp, () -> {
                        smp.messages().send(sender, "economy.baltop-header");
                        int place = 1;
                        for (PlayerProfile profile : profiles) {
                            smp.messages().send(sender, "economy.baltop-line",
                                    "<place>", String.valueOf(place++),
                                    "<player>", profile.name(),
                                    "<balance>", Core.economy().format(profile.balance()));
                        }
                        if (profiles.isEmpty()) {
                            smp.messages().send(sender, "economy.baltop-empty");
                        }
                    }));
        }
    }

    private static final class EcoCommand extends BaseCommand {

        private EcoCommand(SmpPlugin plugin) {
            super(plugin, "eco");
            permission("network.economy.admin");

            sub(operation(plugin, "give", (profile, amount) -> Core.economy().deposit(profile, amount)));
            sub(operation(plugin, "take", (profile, amount) -> Core.economy().withdraw(profile, amount)));
            sub(operation(plugin, "set", (profile, amount) -> Core.economy().set(profile, amount)));
        }

        private static SubCommand operation(SmpPlugin plugin, String name,
                                            BiConsumer<PlayerProfile, Double> action) {
            return new SubCommand(name) {
                @Override
                public void run(CommandSender sender, String[] args) {
                    if (args.length < 2) {
                        plugin.messages().send(sender, "economy.eco-usage");
                        return;
                    }
                    double amount = CommandUtil.parseDouble(args[1], -1);
                    if (amount < 0) {
                        Core.messages().send(sender, "error.invalid-number", "<input>", args[1]);
                        return;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        plugin.messages().send(sender, "economy.target-offline", "<name>", args[0]);
                        return;
                    }
                    PlayerProfile profile = Core.profiles().get(target);
                    action.accept(profile, amount);
                    Core.profiles().save(profile);
                    plugin.messages().send(sender, "economy.eco-done",
                            "<action>", name,
                            "<player>", target.getName(),
                            "<amount>", Core.economy().format(amount),
                            "<balance>", Core.economy().format(profile.balance()));
                }

                @Override
                public List<String> complete(CommandSender sender, String[] args) {
                    return args.length == 1 ? CommandUtil.onlineNames(sender) : List.of();
                }
            }.usage("<player> <amount>");
        }
    }
}

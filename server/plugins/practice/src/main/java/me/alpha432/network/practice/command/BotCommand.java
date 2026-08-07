package me.alpha432.network.practice.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.command.SubCommand;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.bot.BotBehavior;
import me.alpha432.network.practice.bot.BotSettings;
import me.alpha432.network.practice.bot.PracticeBot;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /bot} — spawn a training opponent and tune it while it fights. */
public final class BotCommand extends BaseCommand {

    public BotCommand(PracticePlugin plugin) {
        super(plugin, "bot");
        permission("network.practice.bot");
        playerOnly();

        sub(new SubCommand("spawn", "start") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player player = (Player) sender;
                if (!plugin.hub().isPractice(player)) {
                    plugin.messages().send(sender, "error.not-in-practice");
                    return;
                }
                if (plugin.matches().isInMatch(player) || plugin.ffa().isPlaying(player)
                        || plugin.queue().isQueued(player)) {
                    plugin.messages().send(sender, "bot.busy");
                    return;
                }
                String kitId = args.length > 0 ? args[0] : plugin.defaultKitId();
                if (plugin.kits().get(kitId).isEmpty()) {
                    plugin.messages().send(sender, "bot.unknown-kit", "<kit>", kitId);
                    return;
                }
                BotSettings settings = settingsOf(plugin, player);
                settings.kitId(plugin.kits().get(kitId).orElseThrow().id());

                PracticeBot bot = plugin.bots().spawn(player, settings);
                if (bot == null) {
                    plugin.messages().send(sender, "bot.spawn-failed");
                    return;
                }
                plugin.messages().send(sender, "bot.spawned",
                        "<kit>", plugin.kits().get(kitId).orElseThrow().displayName(),
                        "<difficulty>", String.valueOf(settings.difficulty()),
                        "<mode>", plugin.behaviorName(settings.behavior()));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.kits().names() : List.of();
            }
        }.usage("[kit]").description("Ruft einen Trainings-Bot"));

        sub(new SubCommand("stop", "remove") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player player = (Player) sender;
                PracticeBot bot = plugin.bots().of(player);
                if (bot == null) {
                    plugin.messages().send(sender, "bot.none");
                    return;
                }
                summary(plugin, player, bot);
                plugin.bots().remove(player);
                plugin.hub().send(player);
            }
        }.description("Beendet das Training"));

        sub(new SubCommand("reset") {
            @Override
            public void run(CommandSender sender, String[] args) {
                withBot(plugin, sender, bot -> {
                    plugin.respawnBot(bot);
                    plugin.messages().send(sender, "bot.reset");
                });
            }
        }.description("Setzt den Bot zurück"));

        sub(new SubCommand("difficulty", "diff") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "bot.difficulty-usage");
                    return;
                }
                int value = CommandUtil.parseInt(args[0], -1);
                if (value < BotSettings.MIN_DIFFICULTY || value > BotSettings.MAX_DIFFICULTY) {
                    plugin.messages().send(sender, "bot.difficulty-usage");
                    return;
                }
                BotSettings settings = settingsOf(plugin, (Player) sender);
                settings.difficulty(value);
                plugin.messages().send(sender, "bot.difficulty-set",
                        "<value>", String.valueOf(settings.difficulty()));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? List.of("1", "2", "3", "4", "5") : List.of();
            }
        }.usage("<1-5>").description("Stellt die Schwierigkeit ein"));

        sub(new SubCommand("mode", "behaviour", "verhalten") {
            @Override
            public void run(CommandSender sender, String[] args) {
                BotBehavior behavior = args.length > 0 ? BotBehavior.byName(args[0]) : null;
                if (behavior == null) {
                    plugin.messages().send(sender, "bot.mode-usage",
                            "<modes>", String.join(", ", behaviorNames()));
                    return;
                }
                settingsOf(plugin, (Player) sender).behavior(behavior);
                plugin.messages().send(sender, "bot.mode-set",
                        "<mode>", plugin.behaviorName(behavior));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? behaviorNames() : List.of();
            }
        }.usage("<modus>").description("Wechselt das Verhalten"));

        sub(new SubCommand("kit") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1 || plugin.kits().get(args[0]).isEmpty()) {
                    plugin.messages().send(sender, "bot.kit-usage");
                    return;
                }
                Player player = (Player) sender;
                var kit = plugin.kits().get(args[0]).orElseThrow();
                settingsOf(plugin, player).kitId(kit.id());
                PracticeBot bot = plugin.bots().of(player);
                if (bot != null) {
                    bot.equip(kit);
                    plugin.equipForBotFight(player, kit);
                }
                plugin.messages().send(sender, "bot.kit-set", "<kit>", kit.displayName());
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? plugin.kits().names() : List.of();
            }
        }.usage("<kit>").description("Wechselt das Kit"));

        sub(new SubCommand("health", "leben") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "bot.health-usage");
                    return;
                }
                double value = CommandUtil.parseDouble(args[0], -1);
                if (value <= 0) {
                    plugin.messages().send(sender, "bot.health-usage");
                    return;
                }
                BotSettings settings = settingsOf(plugin, (Player) sender);
                settings.health(value);
                PracticeBot bot = plugin.bots().of((Player) sender);
                if (bot != null) {
                    plugin.respawnBot(bot);
                }
                plugin.messages().send(sender, "bot.health-set",
                        "<value>", String.valueOf(settings.health()));
            }
        }.usage("<herzen x2>").description("Setzt die Lebenspunkte"));

        sub(new SubCommand("knockback", "kb") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "bot.knockback-usage");
                    return;
                }
                double value = CommandUtil.parseDouble(args[0], -1);
                if (value < 0) {
                    plugin.messages().send(sender, "bot.knockback-usage");
                    return;
                }
                BotSettings settings = settingsOf(plugin, (Player) sender);
                settings.knockback(value);
                plugin.messages().send(sender, "bot.knockback-set",
                        "<value>", String.format("%.2f", settings.knockback()));
            }
        }.usage("<faktor>").description("Ändert den Knockback des Bots"));

        sub(new SubCommand("ping") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 1) {
                    plugin.messages().send(sender, "bot.ping-usage");
                    return;
                }
                int value = CommandUtil.parseInt(args[0], -1);
                if (value < 0) {
                    plugin.messages().send(sender, "bot.ping-usage");
                    return;
                }
                BotSettings settings = settingsOf(plugin, (Player) sender);
                settings.pingMillis(value);
                plugin.messages().send(sender, "bot.ping-set",
                        "<value>", String.valueOf(settings.pingMillis()),
                        "<ticks>", String.valueOf(settings.pingTicks()));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? List.of("0", "50", "100", "150", "250") : List.of();
            }
        }.usage("<ms>").description("Simuliert eine Reaktionsverzögerung"));

        sub(new SubCommand("stats", "settings") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player player = (Player) sender;
                BotSettings settings = settingsOf(plugin, player);
                plugin.messages().send(sender, "bot.settings-header");
                plugin.messages().send(sender, "bot.settings-line",
                        "<difficulty>", String.valueOf(settings.difficulty()),
                        "<mode>", plugin.behaviorName(settings.behavior()),
                        "<kit>", plugin.kits().get(settings.kitId())
                                .map(kit -> kit.displayName()).orElse(settings.kitId()),
                        "<health>", String.valueOf(settings.health()),
                        "<knockback>", String.format("%.2f", settings.knockback()),
                        "<ping>", String.valueOf(settings.pingMillis()));
                PracticeBot bot = plugin.bots().of(player);
                if (bot != null) {
                    summary(plugin, player, bot);
                }
            }
        }.description("Zeigt Einstellungen und Auswertung"));
    }

    private static List<String> behaviorNames() {
        List<String> names = new ArrayList<>();
        for (BotBehavior behavior : BotBehavior.values()) {
            names.add(behavior.id());
        }
        return names;
    }

    private static BotSettings settingsOf(PracticePlugin plugin, Player player) {
        PracticeBot bot = plugin.bots().of(player);
        return bot != null ? bot.settings() : plugin.botSettingsOf(player);
    }

    private static void withBot(PracticePlugin plugin, CommandSender sender,
                                java.util.function.Consumer<PracticeBot> action) {
        PracticeBot bot = plugin.bots().of((Player) sender);
        if (bot == null) {
            plugin.messages().send(sender, "bot.none");
            return;
        }
        action.accept(bot);
    }

    /** The round summary shown on /bot stop and /bot stats. */
    private static void summary(PracticePlugin plugin, Player player, PracticeBot bot) {
        plugin.messages().send(player, "bot.summary-header");
        plugin.messages().send(player, "bot.summary-line",
                "<hits>", String.valueOf(bot.stats().hits()),
                "<taken>", String.valueOf(bot.stats().hitsTaken()),
                "<best>", String.valueOf(bot.stats().bestCombo()),
                "<accuracy>", String.format("%.1f", bot.stats().accuracy()),
                "<hps>", String.format("%.2f", bot.stats().hitsPerSecond()),
                "<time>", bot.stats().durationText());
    }
}

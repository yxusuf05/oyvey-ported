package me.alpha432.network.staff.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.staff.Durations;
import me.alpha432.network.staff.StaffPlugin;
import me.alpha432.network.staff.punishment.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** {@code /history} and {@code /banlist}. */
public final class HistoryCommands {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private HistoryCommands() {
    }

    public static void register(StaffPlugin plugin) {
        new HistoryCommand(plugin).register();
        new BanListCommand(plugin).register();
    }

    static String describe(StaffPlugin plugin, Punishment punishment) {
        String state;
        if (!punishment.active()) {
            state = plugin.messages().raw("history.state-lifted");
        } else if (punishment.isExpired()) {
            state = plugin.messages().raw("history.state-expired");
        } else if (punishment.isPermanent()) {
            state = plugin.messages().raw("history.state-permanent");
        } else {
            state = plugin.messages().raw("history.state-active")
                    .replace("<time>", Durations.format(punishment.remainingMillis()));
        }
        return plugin.messages().raw("history.line")
                .replace("<type>", plugin.typeName(punishment.type()))
                .replace("<reason>", punishment.reason())
                .replace("<actor>", punishment.actor())
                .replace("<date>", DATE.format(new Date(punishment.createdAt())))
                .replace("<state>", state);
    }

    private static final class HistoryCommand extends BaseCommand {

        private final StaffPlugin staff;

        private HistoryCommand(StaffPlugin plugin) {
            super(plugin, "history");
            this.staff = plugin;
            permission("network.staff.history");
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            if (args.length == 0) {
                staff.messages().send(sender, "history.usage");
                return;
            }
            int limit = staff.getConfig().getInt("history-size", 15);
            staff.resolve(sender, args[0], profile ->
                    staff.punishments().historyAsync(profile.uuid(), limit).thenAccept(entries ->
                            Bukkit.getScheduler().runTask(staff, () -> {
                                if (entries.isEmpty()) {
                                    staff.messages().send(sender, "history.empty",
                                            "<player>", profile.name());
                                    return;
                                }
                                staff.messages().send(sender, "history.header",
                                        "<player>", profile.name(),
                                        "<count>", String.valueOf(entries.size()));
                                for (Punishment punishment : entries) {
                                    sender.sendMessage(me.alpha432.network.core.text.Msg
                                            .mm(describe(staff, punishment)));
                                }
                            })));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }

    private static final class BanListCommand extends BaseCommand {

        private final StaffPlugin staff;

        private BanListCommand(StaffPlugin plugin) {
            super(plugin, "banlist");
            this.staff = plugin;
            permission("network.staff.banlist");
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            int limit = staff.getConfig().getInt("banlist-size", 25);
            staff.punishments().activeBans(limit).thenAccept(bans ->
                    Bukkit.getScheduler().runTask(staff, () -> {
                        List<Punishment> effective = bans.stream().filter(Punishment::isInEffect).toList();
                        if (effective.isEmpty()) {
                            staff.messages().send(sender, "banlist.empty");
                            return;
                        }
                        staff.messages().send(sender, "banlist.header",
                                "<count>", String.valueOf(effective.size()));
                        for (Punishment ban : effective) {
                            staff.messages().send(sender, "banlist.line",
                                    "<player>", ban.targetName(),
                                    "<reason>", ban.reason(),
                                    "<actor>", ban.actor(),
                                    "<time>", ban.isPermanent()
                                            ? staff.messages().raw("history.state-permanent")
                                            : Durations.format(ban.remainingMillis()));
                        }
                    }));
        }
    }
}

package me.alpha432.network.staff.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.staff.Durations;
import me.alpha432.network.staff.StaffPlugin;
import me.alpha432.network.staff.punishment.Punishment;
import me.alpha432.network.staff.punishment.PunishmentType;
import org.bukkit.command.CommandSender;

import java.util.List;

/** {@code /kick}, {@code /ban}, {@code /tempban}, {@code /mute}, {@code /tempmute}, {@code /warn}. */
public final class PunishCommands {

    private PunishCommands() {
    }

    public static void register(StaffPlugin plugin) {
        new PunishCommand(plugin, "kick", PunishmentType.KICK, false, "network.staff.kick").register();
        new PunishCommand(plugin, "ban", PunishmentType.BAN, false, "network.staff.ban").register();
        new PunishCommand(plugin, "tempban", PunishmentType.BAN, true, "network.staff.tempban").register();
        new PunishCommand(plugin, "mute", PunishmentType.MUTE, false, "network.staff.mute").register();
        new PunishCommand(plugin, "tempmute", PunishmentType.MUTE, true, "network.staff.tempmute").register();
        new PunishCommand(plugin, "warn", PunishmentType.WARN, false, "network.staff.warn").register();
    }

    /** One implementation for every punishment; only type, duration and permission differ. */
    private static final class PunishCommand extends BaseCommand {

        private final StaffPlugin staff;
        private final String label;
        private final PunishmentType type;
        private final boolean timed;

        private PunishCommand(StaffPlugin plugin, String name, PunishmentType type,
                              boolean timed, String permission) {
            super(plugin, name);
            this.staff = plugin;
            this.label = name;
            this.type = type;
            this.timed = timed;
            permission(permission);
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            int reasonIndex = timed ? 2 : 1;
            if (args.length < reasonIndex) {
                staff.messages().send(sender, timed ? "punish.usage-timed" : "punish.usage",
                        "<command>", label);
                return;
            }

            long duration = 0L;
            if (timed) {
                duration = Durations.parse(args[1]);
                if (duration <= 0) {
                    staff.messages().send(sender, "punish.bad-duration", "<input>", args[1]);
                    return;
                }
            }
            String reason = args.length > reasonIndex
                    ? CommandUtil.join(args, reasonIndex)
                    : staff.getConfig().getString("default-reason", "Kein Grund angegeben");

            long finalDuration = duration;
            staff.resolve(sender, args[0], profile -> punish(sender, profile, reason, finalDuration));
        }

        private void punish(CommandSender sender, PlayerProfile target, String reason, long duration) {
            if (!staff.mayPunish(sender, target)) {
                staff.messages().send(sender, "punish.protected", "<player>", target.name());
                return;
            }
            if (type.isLasting() && staff.punishments().activeOf(target.uuid(), type) != null) {
                staff.messages().send(sender, "punish.already-active",
                        "<player>", target.name(), "<type>", staff.typeName(type));
                return;
            }

            Punishment punishment = Punishment.create(
                    target.uuid(), target.name(), type, reason, sender.getName(), duration);
            staff.punishments().addAsync(punishment);
            staff.apply(punishment);
            staff.announce(sender, punishment);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length <= 1) {
                return CommandUtil.onlineNames(sender);
            }
            if (timed && args.length == 2) {
                return List.of("30m", "1h", "6h", "1d", "7d", "30d");
            }
            return List.of();
        }
    }
}

package me.alpha432.network.staff.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.staff.StaffPlugin;
import me.alpha432.network.staff.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

/** {@code /unban} and {@code /unmute}. */
public final class LiftCommands {

    private LiftCommands() {
    }

    public static void register(StaffPlugin plugin) {
        new LiftCommand(plugin, "unban", PunishmentType.BAN, "network.staff.unban").register();
        new LiftCommand(plugin, "unmute", PunishmentType.MUTE, "network.staff.unmute").register();
    }

    private static final class LiftCommand extends BaseCommand {

        private final StaffPlugin staff;
        private final PunishmentType type;

        private LiftCommand(StaffPlugin plugin, String name, PunishmentType type, String permission) {
            super(plugin, name);
            this.staff = plugin;
            this.type = type;
            permission(permission);
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            if (args.length == 0) {
                staff.messages().send(sender, "lift.usage", "<command>", type == PunishmentType.BAN
                        ? "unban" : "unmute");
                return;
            }
            staff.resolve(sender, args[0], profile -> Bukkit.getScheduler().runTaskAsynchronously(staff, () -> {
                int changed = staff.punishments().lift(profile.uuid(), type);
                Bukkit.getScheduler().runTask(staff, () -> {
                    if (changed == 0) {
                        staff.messages().send(sender, "lift.nothing-active",
                                "<player>", profile.name(), "<type>", staff.typeName(type));
                        return;
                    }
                    staff.messages().send(sender, "lift.done",
                            "<player>", profile.name(), "<type>", staff.typeName(type));
                    staff.notifyStaff("lift.broadcast",
                            "<actor>", sender.getName(),
                            "<player>", profile.name(),
                            "<type>", staff.typeName(type));
                    if (type == PunishmentType.MUTE) {
                        var online = Bukkit.getPlayer(profile.uuid());
                        if (online != null) {
                            staff.messages().send(online, "lift.notify-unmute");
                        }
                    }
                });
            }));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }
}

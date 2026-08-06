package me.alpha432.network.staff.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.staff.StaffPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /staffchat [message]} — send one line, or toggle the channel when no text is given. */
public final class StaffChatCommand extends BaseCommand {

    private final StaffPlugin staff;

    public StaffChatCommand(StaffPlugin plugin) {
        super(plugin, "staffchat");
        this.staff = plugin;
        permission("network.staff.staffchat");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length > 0) {
            staff.sendStaffChat(sender.getName(), CommandUtil.join(args, 0));
            return;
        }
        if (!(sender instanceof Player player)) {
            staff.messages().send(sender, "staffchat.usage");
            return;
        }
        boolean enabled = staff.toggleStaffChat(player);
        staff.messages().send(sender, enabled ? "staffchat.enabled" : "staffchat.disabled");
    }
}

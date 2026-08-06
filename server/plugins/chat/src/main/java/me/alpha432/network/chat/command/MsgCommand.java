package me.alpha432.network.chat.command;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /msg <player> <message>} */
public final class MsgCommand extends BaseCommand {

    private final ChatPlugin chat;

    public MsgCommand(ChatPlugin plugin) {
        super(plugin, "msg");
        this.chat = plugin;
        permission("network.chat.msg");
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            chat.messages().send(sender, "msg.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !player(sender).canSee(target)) {
            chat.messages().send(sender, "msg.not-found", "<name>", args[0]);
            return;
        }
        if (target.equals(sender)) {
            chat.messages().send(sender, "msg.self");
            return;
        }
        chat.privateMessages().send(player(sender), target, CommandUtil.join(args, 1));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
    }
}

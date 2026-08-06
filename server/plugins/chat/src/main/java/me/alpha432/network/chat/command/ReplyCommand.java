package me.alpha432.network.chat.command;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /reply <message>} — answers the last private message. */
public final class ReplyCommand extends BaseCommand {

    private final ChatPlugin chat;

    public ReplyCommand(ChatPlugin plugin) {
        super(plugin, "reply");
        this.chat = plugin;
        permission("network.chat.msg");
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            chat.messages().send(sender, "reply.usage");
            return;
        }
        Player partner = chat.privateMessages().lastPartner(player(sender));
        if (partner == null || !player(sender).canSee(partner)) {
            chat.messages().send(sender, "reply.nobody");
            return;
        }
        chat.privateMessages().send(player(sender), partner, CommandUtil.join(args, 0));
    }
}

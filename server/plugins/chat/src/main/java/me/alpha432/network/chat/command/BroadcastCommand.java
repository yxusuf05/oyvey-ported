package me.alpha432.network.chat.command;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.core.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** {@code /broadcast <message>} — announces a message to everyone online. */
public final class BroadcastCommand extends BaseCommand {

    private final ChatPlugin chat;

    public BroadcastCommand(ChatPlugin plugin) {
        super(plugin, "broadcast");
        this.chat = plugin;
        permission("network.chat.broadcast");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            chat.messages().send(sender, "broadcast.usage");
            return;
        }
        String format = chat.getConfig().getString("broadcast-format", "<gold>[Info] <white><message>");
        Bukkit.broadcast(Msg.mm(format.replace("<message>", CommandUtil.join(args, 0))));
    }
}

package me.alpha432.network.chat;

import me.alpha432.network.chat.board.ConfiguredBoardProvider;
import me.alpha432.network.chat.command.BroadcastCommand;
import me.alpha432.network.chat.command.ChatAdminCommand;
import me.alpha432.network.chat.command.IgnoreCommand;
import me.alpha432.network.chat.command.MsgCommand;
import me.alpha432.network.chat.command.ReplyCommand;
import me.alpha432.network.chat.listener.ChatListener;
import me.alpha432.network.chat.listener.JoinQuitListener;
import me.alpha432.network.core.Core;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.core.text.Placeholders;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Chat formatting, private messages, join/leave announcements, tab list and default sidebars. */
public final class ChatPlugin extends JavaPlugin {

    private Messages messages;
    private PrivateMessages privateMessages;
    private ConfiguredBoardProvider boards;
    private boolean chatMuted;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        privateMessages = new PrivateMessages(this);

        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new JoinQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(privateMessages, this);

        new MsgCommand(this).register();
        new ReplyCommand(this).register();
        new IgnoreCommand(this).register();
        new BroadcastCommand(this).register();
        new ChatAdminCommand(this).register();

        boards = new ConfiguredBoardProvider(this);
        Core.boards().register(boards);
        applyTabLayout();

        getLogger().info("NetworkChat enabled.");
    }

    @Override
    public void onDisable() {
        if (boards != null && Core.isReady()) {
            Core.boards().unregister(boards);
        }
    }

    /** Re-reads config.yml, messages.yml and re-applies the tab layout. */
    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        boards.reload();
        applyTabLayout();
    }

    private void applyTabLayout() {
        List<String> header = getConfig().getStringList("tab.header");
        List<String> footer = getConfig().getStringList("tab.footer");
        Core.tabs().headerFooter(
                player -> join(player, header),
                player -> join(player, footer));
    }

    private static Component join(Player player, List<String> lines) {
        if (lines.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(Msg.mm(Placeholders.apply(player, lines.get(i))));
        }
        return result;
    }

    public Messages messages() {
        return messages;
    }

    public PrivateMessages privateMessages() {
        return privateMessages;
    }

    public boolean isChatMuted() {
        return chatMuted;
    }

    public void setChatMuted(boolean chatMuted) {
        this.chatMuted = chatMuted;
    }
}

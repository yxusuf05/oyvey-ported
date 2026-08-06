package me.alpha432.network.lobby.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.lobby.LobbyPlugin;
import me.alpha432.network.lobby.menu.SelectorMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /selector} — opens the server selector without the compass. */
public final class SelectorCommand extends BaseCommand {

    private final LobbyPlugin lobby;

    public SelectorCommand(LobbyPlugin plugin) {
        super(plugin, "selector");
        this.lobby = plugin;
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = player(sender);
        new SelectorMenu(lobby, player).open(player);
    }
}

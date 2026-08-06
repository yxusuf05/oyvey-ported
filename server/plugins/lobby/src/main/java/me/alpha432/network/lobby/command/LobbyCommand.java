package me.alpha432.network.lobby.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.lobby.LobbyPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /lobby} — brings the player back to the hub. */
public final class LobbyCommand extends BaseCommand {

    private final LobbyPlugin lobby;

    public LobbyCommand(LobbyPlugin plugin) {
        super(plugin, "lobby");
        this.lobby = plugin;
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (lobby.lobby().isLobby(player)) {
            lobby.messages().send(sender, "lobby.already-here");
            lobby.lobby().send(player);
            return;
        }
        lobby.messages().send(sender, "lobby.sending");
        lobby.lobby().send(player);
    }
}

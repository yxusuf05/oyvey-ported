package me.alpha432.network.lobby.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.lobby.LobbyPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /setlobby} — stores the hub spawn at the sender's position. */
public final class SetLobbyCommand extends BaseCommand {

    private final LobbyPlugin lobby;

    public SetLobbyCommand(LobbyPlugin plugin) {
        super(plugin, "setlobby");
        this.lobby = plugin;
        permission("network.lobby.admin");
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (!lobby.lobby().isLobby(player)) {
            lobby.messages().send(sender, "lobby.wrong-world", "<world>", lobby.lobby().worldName());
            return;
        }
        lobby.lobby().spawn(player.getLocation());
        lobby.messages().send(sender, "lobby.spawn-set",
                "<location>", Locations.pretty(player.getLocation()));
    }
}

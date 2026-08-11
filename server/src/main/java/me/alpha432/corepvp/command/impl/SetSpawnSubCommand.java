package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.lobby.LobbyService;
import me.alpha432.corepvp.util.Locations;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetSpawnSubCommand extends SubCommand {

    private final LobbyService lobby;
    private final Messages messages;

    public SetSpawnSubCommand(LobbyService lobby, Messages messages) {
        super("setspawn", "corepvp.command.admin", "setspawn",
                "Set the lobby spawn to where you are standing.", true);
        this.lobby = lobby;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        lobby.setSpawn(Locations.centered(player.getLocation()));
        messages.send(player, "lobby.spawn-set");
    }
}

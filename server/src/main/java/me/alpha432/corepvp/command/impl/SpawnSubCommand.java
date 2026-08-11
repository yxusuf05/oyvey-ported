package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.lobby.LobbyService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnSubCommand extends SubCommand {

    private final LobbyService lobby;
    private final Messages messages;

    public SpawnSubCommand(LobbyService lobby, Messages messages) {
        super("spawn", null, "spawn", "Return to the hub.", true, "hub", "lobby");
        this.lobby = lobby;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (lobby.spawn() == null) {
            messages.send(player, "lobby.no-spawn");
            return;
        }
        lobby.sendToLobby(player);
        messages.send(player, "lobby.teleported");
    }
}

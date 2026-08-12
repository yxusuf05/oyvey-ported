package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.lobby.LobbyService;
import me.alpha432.corepvp.util.Locations;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetSpawnSubCommand extends SubCommand {

    private final LobbyService lobby;
    private final me.alpha432.corepvp.survival.SurvivalService survival;
    private final Messages messages;

    public SetSpawnSubCommand(LobbyService lobby,
                              me.alpha432.corepvp.survival.SurvivalService survival,
                              Messages messages) {
        super("setspawn", "corepvp.command.admin", "setspawn [lobby|survival]",
                "Set a spawn to where you are standing.", true);
        this.lobby = lobby;
        this.survival = survival;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        boolean survivalSpawn = args.length > 0 && args[0].equalsIgnoreCase("survival");

        if (survivalSpawn) {
            survival.setSpawn(Locations.centered(player.getLocation()));
            messages.send(player, "survival.spawn-set");
            return;
        }
        lobby.setSpawn(Locations.centered(player.getLocation()));
        messages.send(player, "lobby.spawn-set");
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length <= 1 ? java.util.List.of("lobby", "survival") : java.util.List.of();
    }
}

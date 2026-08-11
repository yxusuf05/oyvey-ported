package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.match.Match;
import me.alpha432.corepvp.state.PlayerState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /leave} - stop spectating, or give up a match you are losing. */
public final class LeaveCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;

    public LeaveCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        PlayerState state = plugin.states().state(player);

        if (state == PlayerState.SPECTATING) {
            plugin.matches().stopSpectating(player);
            messages.send(player, "spectate.left");
            return;
        }

        Match match = plugin.matches().matchOf(player);
        if (match != null && match.contains(player.getUniqueId())) {
            // Treated exactly like disconnecting: the opponent gets the win.
            plugin.matches().handleQuit(player);
            plugin.lobby().sendToLobby(player);
            messages.send(player, "match.left");
            return;
        }

        messages.send(player, "match.nothing-to-leave");
    }
}

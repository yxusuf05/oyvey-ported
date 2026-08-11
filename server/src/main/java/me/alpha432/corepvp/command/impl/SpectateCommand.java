package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.profile.ProfileSettings;
import me.alpha432.corepvp.state.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /spectate <player>} */
public final class SpectateCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;

    public SpectateCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            messages.send(player, "spectate.usage");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "general.player-not-found", Messages.of("input", args[0]));
            return;
        }
        if (plugin.states().state(player) != PlayerState.LOBBY) {
            messages.send(player, "spectate.not-in-lobby");
            return;
        }

        Profile targetProfile = plugin.profiles().get(target);
        boolean allowed = targetProfile == null
                || targetProfile.settings().get(ProfileSettings.Flag.ALLOW_SPECTATORS)
                || player.hasPermission("corepvp.spectate.bypass");
        if (!allowed) {
            messages.send(player, "spectate.disabled", Messages.of("player", target.getName()));
            return;
        }

        if (!plugin.matches().spectate(player, target)) {
            messages.send(player, "spectate.not-fighting", Messages.of("player", target.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(online -> plugin.matches().matchOf(online) != null)
                    .map(Player::getName)
                    .toList();
        }
        return List.of();
    }
}

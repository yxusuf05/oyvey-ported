package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.elo.StatsMenu;
import me.alpha432.corepvp.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /stats [player]} */
public final class StatsCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;

    public StatsCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;

        Profile profile;
        if (args.length == 0) {
            profile = plugin.profiles().require(player);
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                // Offline lookups would need a database round trip; the menu is
                // built from a live profile, so this stays to online players.
                messages.send(player, "general.player-not-found", Messages.of("input", args[0]));
                return;
            }
            profile = plugin.profiles().require(target);
        }
        new StatsMenu(plugin, profile).open(player);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length <= 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : List.of();
    }
}

package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.elo.LeaderboardMenu;
import me.alpha432.corepvp.kit.Kit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /leaderboard [kit]} */
public final class LeaderboardCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;

    public LeaderboardCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;

        String kitId = null;
        if (args.length > 0 && plugin.kits().exists(args[0])) {
            kitId = plugin.kits().byId(args[0]).id();
        } else {
            List<Kit> kits = plugin.kits().enabled();
            if (!kits.isEmpty()) {
                kitId = kits.get(0).id();
            }
        }
        if (kitId == null) {
            messages.send(player, "kit.none");
            return;
        }

        plugin.leaderboards().refresh(kitId);
        new LeaderboardMenu(plugin, kitId).open(player);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length <= 1 ? plugin.kits().ids() : List.of();
    }
}

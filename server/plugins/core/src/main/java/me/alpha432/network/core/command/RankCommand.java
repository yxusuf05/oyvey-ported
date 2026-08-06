package me.alpha432.network.core.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.CorePlugin;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /rank list|info|set} */
public final class RankCommand extends BaseCommand {

    public RankCommand(CorePlugin plugin) {
        super(plugin, "rank");
        permission("network.command.rank");

        sub(new SubCommand("list") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Core.messages().send(sender, "rank.list-header");
                for (Rank rank : Core.ranks().sortedByWeight()) {
                    Core.messages().send(sender, "rank.list-line",
                            "<id>", rank.id(),
                            "<display>", rank.displayName(),
                            "<weight>", String.valueOf(rank.weight()),
                            "<permissions>", String.valueOf(Core.ranks().resolvePermissions(rank).size()));
                }
            }
        }.description("Lists every rank"));

        sub(new SubCommand("info") {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0])
                        : (sender instanceof Player player ? player : null);
                if (target == null) {
                    Core.messages().send(sender, "error.player-not-found",
                            "<name>", args.length > 0 ? args[0] : "?");
                    return;
                }
                Rank rank = Core.ranks().of(target);
                Core.messages().send(sender, "rank.info",
                        "<player>", target.getName(),
                        "<id>", rank.id(),
                        "<display>", rank.displayName(),
                        "<weight>", String.valueOf(rank.weight()));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? CommandUtil.onlineNames(sender) : List.of();
            }
        }.usage("[player]").description("Shows the rank of a player"));

        sub(new SubCommand("set") {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length < 2) {
                    Core.messages().send(sender, "rank.set-usage");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Core.messages().send(sender, "error.player-not-found", "<name>", args[0]);
                    return;
                }
                if (!Core.ranks().exists(args[1])) {
                    Core.messages().send(sender, "rank.unknown", "<rank>", args[1]);
                    return;
                }
                if (!Core.ranks().set(target, args[1])) {
                    Core.messages().send(sender, "rank.set-failed", "<player>", target.getName());
                    return;
                }
                Core.tabs().refreshAll();
                Core.messages().send(sender, "rank.set-success",
                        "<player>", target.getName(), "<rank>", args[1].toLowerCase());
                Core.messages().send(target, "rank.set-notify", "<rank>", args[1].toLowerCase());
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return CommandUtil.onlineNames(sender);
                }
                if (args.length == 2) {
                    List<String> ids = new ArrayList<>();
                    Core.ranks().all().forEach(rank -> ids.add(rank.id()));
                    return ids;
                }
                return List.of();
            }
        }.usage("<player> <rank>").description("Changes the rank of a player"));
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }
        // A bare "/rank <player>" is treated as "/rank info <player>".
        PlayerProfile profile = Core.profiles().byName(args[0]).orElse(null);
        if (profile == null) {
            sendUsage(sender);
            return;
        }
        Rank rank = Core.ranks().of(profile);
        Core.messages().send(sender, "rank.info",
                "<player>", profile.name(),
                "<id>", rank.id(),
                "<display>", rank.displayName(),
                "<weight>", String.valueOf(rank.weight()));
    }
}

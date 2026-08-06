package me.alpha432.network.practice.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.match.Match;
import me.alpha432.network.practice.menu.FfaMenu;
import me.alpha432.network.practice.menu.LeaderboardMenu;
import me.alpha432.network.practice.menu.PracticeMenu;
import me.alpha432.network.practice.menu.StatsMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** The player facing practice commands. */
public final class PracticeCommands {

    private PracticeCommands() {
    }

    public static void register(PracticePlugin plugin) {
        new PracticeCommand(plugin).register();
        new LeaveCommand(plugin).register();
        new SpectateCommand(plugin).register();
        new StatsCommand(plugin).register();
        new LeaderboardCommand(plugin).register();
        new FfaCommand(plugin).register();
    }

    private static final class PracticeCommand extends BaseCommand {

        private final PracticePlugin practice;

        private PracticeCommand(PracticePlugin plugin) {
            super(plugin, "practice");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (!practice.hub().isPractice(player)) {
                practice.messages().send(sender, "error.not-in-practice");
                return;
            }
            new PracticeMenu(practice, player).open(player);
        }
    }

    private static final class LeaveCommand extends BaseCommand {

        private final PracticePlugin practice;

        private LeaveCommand(PracticePlugin plugin) {
            super(plugin, "leave");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (practice.queue().leave(player)) {
                practice.messages().send(sender, "queue.left");
                return;
            }
            if (practice.ffa().isPlaying(player)) {
                practice.ffa().leave(player, true);
                practice.messages().send(sender, "ffa.left");
                return;
            }
            if (practice.matches().isSpectator(player)) {
                practice.matches().removeSpectator(player);
                practice.messages().send(sender, "spectate.left");
                return;
            }
            Match match = practice.matches().matchOf(player);
            if (match != null) {
                practice.matches().end(match, match.opponentOf(player.getUniqueId()),
                        "match.reason-forfeit");
                return;
            }
            if (practice.hub().isPractice(player)) {
                // Nothing to leave inside practice, so go back to the lobby.
                practice.sendToLobby(player);
                return;
            }
            practice.messages().send(sender, "error.nothing-to-leave");
        }
    }

    private static final class SpectateCommand extends BaseCommand {

        private final PracticePlugin practice;

        private SpectateCommand(PracticePlugin plugin) {
            super(plugin, "spectate");
            this.practice = plugin;
            permission("network.practice.spectate");
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length == 0) {
                practice.messages().send(sender, "spectate.usage");
                return;
            }
            if (practice.matches().isInMatch(player)) {
                practice.messages().send(sender, "spectate.busy");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                practice.messages().send(sender, "error.player-offline", "<name>", args[0]);
                return;
            }
            Match match = practice.matches().matchOf(target);
            if (match == null || !match.contains(target.getUniqueId())) {
                practice.messages().send(sender, "spectate.not-fighting", "<player>", target.getName());
                return;
            }
            practice.matches().addSpectator(player, match);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }

    private static final class StatsCommand extends BaseCommand {

        private final PracticePlugin practice;

        private StatsCommand(PracticePlugin plugin) {
            super(plugin, "stats");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length == 0) {
                new StatsMenu(practice, player, player.getUniqueId(), player.getName()).open(player);
                return;
            }
            Core.profiles().lookup(args[0]).thenAccept(profile ->
                    Bukkit.getScheduler().runTask(practice, () -> {
                        if (profile == null) {
                            practice.messages().send(sender, "error.player-unknown", "<name>", args[0]);
                            return;
                        }
                        new StatsMenu(practice, player, profile.uuid(), profile.name()).open(player);
                    }));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }

    private static final class LeaderboardCommand extends BaseCommand {

        private final PracticePlugin practice;

        private LeaderboardCommand(PracticePlugin plugin) {
            super(plugin, "leaderboard");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            new LeaderboardMenu(practice, player).open(player);
        }
    }

    private static final class FfaCommand extends BaseCommand {

        private final PracticePlugin practice;

        private FfaCommand(PracticePlugin plugin) {
            super(plugin, "ffa");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (!practice.hub().isPractice(player)) {
                practice.messages().send(sender, "error.not-in-practice");
                return;
            }
            if (args.length == 0) {
                new FfaMenu(practice, player).open(player);
                return;
            }
            practice.ffa().get(args[0]).ifPresentOrElse(
                    arena -> practice.joinFfa(player, arena),
                    () -> practice.messages().send(sender, "ffa.unknown", "<name>", args[0]));
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? practice.ffa().names() : List.of();
        }
    }
}

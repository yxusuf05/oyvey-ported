package me.alpha432.corepvp.elo;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.match.Match;
import me.alpha432.corepvp.match.MatchTeam;
import me.alpha432.corepvp.profile.KitStats;
import me.alpha432.corepvp.profile.Profile;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Turns a finished match into win/loss records and, when ranked, ELO changes. */
public final class EloService {

    private final CorePvPPlugin plugin;
    private final Messages messages;

    public EloService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    public void apply(Match match, MatchTeam winner) {
        if (winner == null) {
            return;
        }
        String kitId = match.kit().id();

        for (MatchTeam team : match.teams()) {
            boolean won = team == winner;
            for (UUID uuid : team.members()) {
                Profile profile = plugin.profiles().get(uuid);
                if (profile == null) {
                    continue;
                }
                KitStats stats = profile.kit(kitId);
                if (won) {
                    stats.wins(stats.wins() + 1);
                } else {
                    stats.losses(stats.losses() + 1);
                }
                profile.markDirty();
            }
        }

        if (!match.type().ranked()) {
            return;
        }

        MatchTeam loser = match.opposingTeam(winner);
        if (loser == null) {
            return;
        }

        int winnerElo = averageElo(winner, kitId);
        int loserElo = averageElo(loser, kitId);
        int base = plugin.configs().main().getInt("queue.k-factor", 32);
        int kFactor = Elo.kFactor(matchesPlayed(winner, kitId), winnerElo, base);
        int change = Elo.gain(winnerElo, loserElo, kFactor);

        award(winner, kitId, change, true);
        award(loser, kitId, change, false);
    }

    private void award(MatchTeam team, String kitId, int change, boolean won) {
        for (UUID uuid : team.members()) {
            Profile profile = plugin.profiles().get(uuid);
            if (profile == null) {
                continue;
            }
            KitStats stats = profile.kit(kitId);
            int before = stats.elo();
            int after = won ? before + change : Elo.applyLoss(before, change);
            stats.elo(after);
            profile.markDirty();

            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                messages.send(player, won ? "elo.gained" : "elo.lost",
                        Messages.of("change", Math.abs(after - before)),
                        Messages.of("elo", after));
            }
        }
    }

    /** A team's rating is the average of its members, so 2v2 is not skewed by one carry. */
    private int averageElo(MatchTeam team, String kitId) {
        int total = 0;
        int counted = 0;
        for (UUID uuid : team.members()) {
            Profile profile = plugin.profiles().get(uuid);
            if (profile != null) {
                total += profile.kit(kitId).elo();
                counted++;
            }
        }
        return counted == 0 ? Elo.DEFAULT : total / counted;
    }

    private int matchesPlayed(MatchTeam team, String kitId) {
        int lowest = Integer.MAX_VALUE;
        for (UUID uuid : team.members()) {
            Profile profile = plugin.profiles().get(uuid);
            if (profile != null) {
                lowest = Math.min(lowest, profile.kit(kitId).played());
            }
        }
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }
}

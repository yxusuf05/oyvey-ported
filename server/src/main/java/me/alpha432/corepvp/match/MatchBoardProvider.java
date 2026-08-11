package me.alpha432.corepvp.match;

import me.alpha432.corepvp.board.BoardProvider;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** The sidebar shown while fighting or spectating. */
public final class MatchBoardProvider implements BoardProvider {

    private final Messages messages;
    private final MatchManager matches;

    public MatchBoardProvider(Messages messages, MatchManager matches) {
        this.messages = messages;
        this.matches = matches;
    }

    @Override
    public Component title(Player player) {
        return messages.render("scoreboard.match.title");
    }

    @Override
    public List<Component> lines(Player player) {
        Match match = matches.matchOf(player);
        if (match == null) {
            return List.of();
        }

        MatchTeam own = match.teamOf(player.getUniqueId());
        MatchTeam other = own == null ? match.teams().get(0) : match.opposingTeam(own);
        Player opponent = firstOnline(other);

        String key = match.kit().flags().boxing()
                ? "scoreboard.match.boxing-lines"
                : "scoreboard.match.lines";

        return messages.renderList(key,
                Messages.of("kit", match.kit().displayName()),
                Messages.of("duration", TimeUtil.clock(match.durationMillis())),
                Messages.of("opponent", other == null ? "-" : other.displayName()),
                Messages.of("ping", opponent == null ? 0 : opponent.getPing()),
                Messages.of("your_hits", own == null ? 0 : match.stats(player.getUniqueId()).hits()),
                Messages.of("their_hits", opponent == null ? 0 : match.stats(opponent.getUniqueId()).hits()),
                Messages.of("combo", match.stats(player.getUniqueId()).longestCombo()));
    }

    private Player firstOnline(MatchTeam team) {
        if (team == null) {
            return null;
        }
        for (UUID uuid : team.members()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }
}

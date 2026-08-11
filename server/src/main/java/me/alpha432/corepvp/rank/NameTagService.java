package me.alpha432.corepvp.rank;

import me.alpha432.corepvp.board.BoardService;
import me.alpha432.corepvp.board.PlayerBoard;
import me.alpha432.corepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Rank prefixes above players' heads and in the tab list.
 *
 * <p>Every player has their own {@link Scoreboard} so they can have their own
 * sidebar, and a scoreboard only knows about the teams registered on it. Teams
 * therefore have to be replicated onto every player's board - forgetting this
 * is why per-player sidebars usually break name tags.
 */
public final class NameTagService {

    private final RankManager ranks;
    private final BoardService boards;

    public NameTagService(RankManager ranks, BoardService boards) {
        this.ranks = ranks;
        this.boards = boards;
    }

    /** Fills a freshly created board with teams for everyone already online. */
    public void populate(Player viewer) {
        PlayerBoard board = boards.board(viewer);
        if (board == null) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            applyTo(board.scoreboard(), target);
        }
    }

    /** Pushes one player's rank onto every other player's board. */
    public void update(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = boards.board(viewer);
            if (board != null) {
                applyTo(board.scoreboard(), target);
            }
        }
    }

    public void updateAll() {
        for (Player target : Bukkit.getOnlinePlayers()) {
            update(target);
        }
    }

    public void remove(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = boards.board(viewer);
            if (board == null) {
                continue;
            }
            Team team = board.scoreboard().getEntryTeam(target.getName());
            if (team != null) {
                team.unregister();
            }
        }
    }

    /**
     * Turns collisions on or off for one player everywhere. Handy for the hub,
     * where being shoved around by other players is only annoying.
     */
    public void collidable(Player target, boolean collidable) {
        Team.OptionStatus status = collidable ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = boards.board(viewer);
            if (board == null) {
                continue;
            }
            Team team = board.scoreboard().getEntryTeam(target.getName());
            if (team != null) {
                team.setOption(Team.Option.COLLISION_RULE, status);
            }
        }
    }

    private void applyTo(Scoreboard scoreboard, Player target) {
        Rank rank = ranks.of(target);
        String teamName = rank.teamName(target.getName());

        // The player may have changed rank, which changes their team name.
        Team previous = scoreboard.getEntryTeam(target.getName());
        if (previous != null && !previous.getName().equals(teamName)) {
            previous.removeEntry(target.getName());
            if (previous.getEntries().isEmpty()) {
                previous.unregister();
            }
        }

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        if (!rank.prefix().isEmpty()) {
            team.prefix(Text.mini(rank.prefix()));
        }
        team.color(rank.color());
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }
    }
}

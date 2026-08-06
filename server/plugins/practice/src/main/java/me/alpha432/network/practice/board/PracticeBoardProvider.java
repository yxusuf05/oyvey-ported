package me.alpha432.network.practice.board;

import me.alpha432.network.core.board.BoardProvider;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.match.Match;
import me.alpha432.network.practice.queue.QueueService;
import me.alpha432.network.practice.stats.KitStats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar for the practice area. Runs above the default board so a running match replaces the
 * normal lines while the fight lasts.
 */
public final class PracticeBoardProvider implements BoardProvider {

    private final PracticePlugin plugin;

    public PracticeBoardProvider(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean appliesTo(Player player) {
        return plugin.hub().isPractice(player);
    }

    @Override
    public Component title(Player player) {
        return plugin.messages().get("board.title");
    }

    @Override
    public List<Component> lines(Player player) {
        Match match = plugin.matches().matchOf(player);
        if (match != null) {
            return matchLines(player, match);
        }
        QueueService.Entry entry = plugin.queue().entryOf(player);
        if (entry != null) {
            return queueLines(player, entry);
        }
        if (plugin.ffa().isPlaying(player)) {
            return ffaLines(player);
        }
        return hubLines(player);
    }

    private List<Component> matchLines(Player player, Match match) {
        boolean spectating = plugin.matches().isSpectator(player);
        Player opponent = spectating
                ? Bukkit.getPlayer(match.second())
                : Bukkit.getPlayer(match.opponentOf(player.getUniqueId()));
        Player self = spectating ? Bukkit.getPlayer(match.first()) : player;

        List<String> raw = plugin.messages().rawList(spectating ? "board.spectating" : "board.match");
        List<Component> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(Msg.mm(line
                    .replace("<opponent>", opponent == null ? "-" : opponent.getName())
                    .replace("<self>", self == null ? "-" : self.getName())
                    .replace("<ping>", opponent == null ? "0" : String.valueOf(opponent.getPing()))
                    .replace("<kit>", match.kit().displayName())
                    .replace("<arena>", match.arena().displayName())
                    .replace("<duration>", match.durationText())
                    .replace("<mode>", plugin.messages().raw(match.isRanked()
                            ? "board.mode-ranked" : "board.mode-unranked"))
                    .replace("<hits>", String.valueOf(match.hitsOf(player.getUniqueId())))
                    .replace("<combo>", String.valueOf(match.comboOf(player.getUniqueId())))
                    .replace("<opponent_hits>", opponent == null ? "0"
                            : String.valueOf(match.hitsOf(opponent.getUniqueId())))));
        }
        return lines;
    }

    private List<Component> queueLines(Player player, QueueService.Entry entry) {
        List<Component> lines = new ArrayList<>();
        for (String line : plugin.messages().rawList("board.queue")) {
            lines.add(Msg.mm(line
                    .replace("<kit>", plugin.kits().get(entry.kit())
                            .map(kit -> kit.displayName()).orElse(entry.kit()))
                    .replace("<mode>", plugin.messages().raw(entry.ranked()
                            ? "board.mode-ranked" : "board.mode-unranked"))
                    .replace("<map>", entry.arena() == null
                            ? plugin.messages().raw("board.map-random") : entry.arena())
                    .replace("<waited>", String.valueOf(entry.waitedSeconds()))
                    .replace("<elo>", String.valueOf(entry.elo()))
                    .replace("<queued>", String.valueOf(plugin.queue().size(entry.kit(), entry.ranked())))));
        }
        return lines;
    }

    private List<Component> ffaLines(Player player) {
        String arenaId = plugin.ffa().arenaOf(player);
        List<Component> lines = new ArrayList<>();
        for (String line : plugin.messages().rawList("board.ffa")) {
            lines.add(Msg.mm(line
                    .replace("<arena>", plugin.ffa().get(arenaId)
                            .map(arena -> arena.displayName()).orElse("-"))
                    .replace("<streak>", String.valueOf(plugin.ffa().killstreak(player)))
                    .replace("<players>", String.valueOf(plugin.ffa().playerCount(arenaId)))));
        }
        return lines;
    }

    private List<Component> hubLines(Player player) {
        List<KitStats> sorted = plugin.stats().sortedOf(player.getUniqueId());
        KitStats best = sorted.isEmpty() ? null : sorted.get(0);
        int wins = 0;
        int losses = 0;
        for (KitStats stats : sorted) {
            wins += stats.wins();
            losses += stats.losses();
        }
        List<Component> lines = new ArrayList<>();
        for (String line : plugin.messages().rawList("board.hub")) {
            lines.add(Msg.mm(line
                    .replace("<elo>", String.valueOf(plugin.stats().averageElo(player.getUniqueId())))
                    .replace("<tier>", plugin.tierName(best == null ? "bronze" : best.tier()))
                    .replace("<wins>", String.valueOf(wins))
                    .replace("<losses>", String.valueOf(losses))
                    .replace("<queued>", String.valueOf(plugin.queue().size()))
                    .replace("<fighting>", String.valueOf(plugin.matches().fightingPlayers()))
                    .replace("<online>", String.valueOf(
                            Bukkit.getWorld(plugin.hub().worldName()) == null ? 0
                                    : Bukkit.getWorld(plugin.hub().worldName()).getPlayers().size()))));
        }
        return lines;
    }
}

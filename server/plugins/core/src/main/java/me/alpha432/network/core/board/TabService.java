package me.alpha432.network.core.board;

import me.alpha432.network.core.rank.Rank;
import me.alpha432.network.core.rank.RankService;
import me.alpha432.network.core.text.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Owns the tab list: header and footer, the coloured entry names, and the sort order. Sorting
 * uses one team per player on every viewer's scoreboard, named so that a higher rank weight
 * sorts further up.
 */
public final class TabService implements Listener {

    private static final String TEAM_PREFIX = "tab";

    private final Plugin plugin;
    private final BoardService boards;
    private final RankService ranks;
    private Function<Player, Component> header = player -> Component.empty();
    private Function<Player, Component> footer = player -> Component.empty();
    private BukkitTask task;

    public TabService(Plugin plugin, BoardService boards, RankService ranks) {
        this.plugin = plugin;
        this.boards = boards;
        this.ranks = ranks;
    }

    public void headerFooter(Function<Player, Component> header, Function<Player, Component> footer) {
        this.header = header;
        this.footer = footer;
    }

    /** Refreshes header and footer regularly; entry names only change on join, quit or rank change. */
    public void start(long periodTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendHeaderFooter(player);
            }
        }, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void sendHeaderFooter(Player player) {
        player.sendPlayerListHeaderAndFooter(header.apply(player), footer.apply(player));
    }

    /** Recomputes list names and sorting teams for everyone. */
    public void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            syncTeams(viewer);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyListName(player);
        }
    }

    public void applyListName(Player player) {
        Rank rank = ranks.of(player);
        player.playerListName(Msg.mm(rank.prefix() + rank.nameColor() + player.getName() + rank.suffix()));
    }

    private void syncTeams(Player viewer) {
        Scoreboard scoreboard = boards.scoreboardOf(viewer);
        Map<String, Player> desired = new HashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            desired.put(uniqueTeamName(desired, target), target);
        }

        List<Team> stale = new ArrayList<>();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX) && !desired.containsKey(team.getName())) {
                stale.add(team);
            }
        }
        stale.forEach(Team::unregister);

        for (Map.Entry<String, Player> entry : desired.entrySet()) {
            Team team = scoreboard.getTeam(entry.getKey());
            if (team == null) {
                team = scoreboard.registerNewTeam(entry.getKey());
            }
            Rank rank = ranks.of(entry.getValue());
            team.prefix(Msg.mm(rank.prefix()));
            team.color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
            String name = entry.getValue().getName();
            if (!team.hasEntry(name)) {
                team.addEntry(name);
            }
        }
    }

    /**
     * Teams sort alphabetically, so the name starts with the inverted rank weight. Bukkit caps
     * team names at 16 characters, hence the truncation.
     */
    private String uniqueTeamName(Map<String, Player> taken, Player player) {
        int weight = Math.max(0, Math.min(999, 999 - ranks.of(player).weight()));
        String base = TEAM_PREFIX + String.format("%03d", weight);
        String name = trim(base + player.getName());
        int suffix = 0;
        while (taken.containsKey(name)) {
            name = trim(base + player.getName()) + (++suffix);
            name = trim(name);
        }
        return name;
    }

    private static String trim(String value) {
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshAll();
            sendHeaderFooter(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::refreshAll);
    }
}

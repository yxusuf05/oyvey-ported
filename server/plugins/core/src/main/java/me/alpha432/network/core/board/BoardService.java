package me.alpha432.network.core.board;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a flicker free sidebar. Every player gets their own scoreboard; lines are teams whose
 * prefix carries the text, so an update only changes a prefix instead of removing entries.
 */
public final class BoardService implements Listener {

    /** Vanilla allows 15 sidebar rows. */
    public static final int MAX_LINES = 15;
    private static final String OBJECTIVE = "network";
    private static final String ENTRY_CHARS = "0123456789abcdef";

    private final Plugin plugin;
    private final List<BoardProvider> providers = new ArrayList<>();
    private final Map<UUID, PlayerBoard> boards = new HashMap<>();
    private BukkitTask task;

    public BoardService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(long periodTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void register(BoardProvider provider) {
        providers.add(provider);
        providers.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    }

    public void unregister(BoardProvider provider) {
        providers.remove(provider);
    }

    /** The personal scoreboard of a player, created on demand. Shared with the tab list. */
    public Scoreboard scoreboardOf(Player player) {
        return board(player).scoreboard;
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                update(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Sidebar update failed for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public void update(Player player) {
        BoardProvider provider = providerFor(player);
        PlayerBoard board = board(player);
        if (provider == null) {
            board.hide();
            return;
        }
        List<Component> lines = provider.lines(player);
        board.render(provider.title(player), lines);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }

    private BoardProvider providerFor(Player player) {
        for (BoardProvider provider : providers) {
            if (provider.appliesTo(player)) {
                return provider;
            }
        }
        return null;
    }

    private PlayerBoard board(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), id -> new PlayerBoard(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Creating the board also assigns it, which the tab list relies on.
        board(event.getPlayer());
        update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    /** Holds one player's scoreboard, its sidebar objective and the 15 line teams. */
    private static final class PlayerBoard {

        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Player player;
        private int visibleLines;

        private PlayerBoard(Player player) {
            this.player = player;
            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            this.objective = scoreboard.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty());
            for (int i = 0; i < MAX_LINES; i++) {
                Team team = scoreboard.registerNewTeam("line-" + i);
                team.addEntry(entry(i));
            }
            player.setScoreboard(scoreboard);
        }

        private void render(Component title, List<Component> lines) {
            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
            objective.displayName(title);
            if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            int count = Math.min(lines.size(), MAX_LINES);
            for (int i = 0; i < count; i++) {
                Team team = scoreboard.getTeam("line-" + i);
                if (team != null) {
                    team.prefix(lines.get(i));
                }
                objective.getScore(entry(i)).setScore(count - i);
            }
            for (int i = count; i < visibleLines; i++) {
                scoreboard.resetScores(entry(i));
            }
            visibleLines = count;
        }

        private void hide() {
            for (int i = 0; i < visibleLines; i++) {
                scoreboard.resetScores(entry(i));
            }
            visibleLines = 0;
            objective.setDisplaySlot(null);
        }

        /**
         * A unique, invisible entry per row. Two colour codes render as nothing but keep the
         * scoreboard entries distinct.
         */
        private static String entry(int index) {
            return "§" + ENTRY_CHARS.charAt(index % ENTRY_CHARS.length()) + "§r";
        }
    }
}

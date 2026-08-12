package me.alpha432.corepvp.elo;

import me.alpha432.corepvp.storage.Database;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Top players per kit.
 *
 * <p>Refreshed on a timer and served from cache: a leaderboard that queries the
 * database every time a menu opens is a query per click.
 */
public final class LeaderboardService {

    public record Row(int position, String name, int elo, int wins, int losses) {
    }

    private static final String QUERY = """
            SELECT p.name AS name, s.elo AS elo, s.wins AS wins, s.losses AS losses
            FROM corepvp_kit_stats s
            JOIN corepvp_profiles p ON p.uuid = s.uuid
            WHERE s.kit = ? AND (s.wins + s.losses) > 0
            ORDER BY s.elo DESC
            LIMIT ?
            """;

    private final Plugin plugin;
    private final Database database;
    private final int limit;
    private final Map<String, List<Row>> cache = new ConcurrentHashMap<>();

    private BukkitTask task;

    public LeaderboardService(Plugin plugin, Database database, int limit) {
        this.plugin = plugin;
        this.database = database;
        this.limit = Math.max(1, limit);
    }

    public void start(List<String> kitIds, long periodSeconds) {
        stop();
        long ticks = Math.max(20L, periodSeconds * 20L);
        task = Tasks.asyncTimer(() -> kitIds.forEach(this::refresh), 20L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public List<Row> top(String kitId) {
        return cache.getOrDefault(kitId.toLowerCase(Locale.ROOT), List.of());
    }

    public void refresh(String kitId) {
        String key = kitId.toLowerCase(Locale.ROOT);
        database.query(connection -> {
            List<Row> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(QUERY)) {
                statement.setString(1, key);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    int position = 1;
                    while (result.next()) {
                        rows.add(new Row(position++, result.getString("name"),
                                result.getInt("elo"), result.getInt("wins"), result.getInt("losses")));
                    }
                }
            }
            return rows;
        }).thenAccept(rows -> cache.put(key, rows)).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Could not refresh the " + key + " leaderboard", throwable);
            return null;
        });
    }
}

package me.alpha432.network.practice.stats;

import me.alpha432.network.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Per kit records and ratings. Cached while the player is online, written through on change. */
public final class StatsService {

    private final Plugin plugin;
    private final Database database;
    private final Map<UUID, Map<String, KitStats>> cache = new ConcurrentHashMap<>();
    private final int startingElo;

    public StatsService(Plugin plugin, Database database, int startingElo) {
        this.plugin = plugin;
        this.database = database;
        this.startingElo = startingElo;
    }

    /** Never returns null; an unknown pair starts at the configured rating. */
    public KitStats get(UUID uuid, String kit) {
        return cache.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(kit, id -> KitStats.fresh(uuid, kit, startingElo));
    }

    public Map<String, KitStats> allOf(UUID uuid) {
        return cache.getOrDefault(uuid, Map.of());
    }

    /** Average rating across the kits the player actually played. */
    public int averageElo(UUID uuid) {
        Map<String, KitStats> stats = allOf(uuid);
        if (stats.isEmpty()) {
            return startingElo;
        }
        int total = 0;
        for (KitStats entry : stats.values()) {
            total += entry.elo();
        }
        return total / stats.size();
    }

    public void save(KitStats stats) {
        database.executeAsync(database.upsert("practice_stats",
                        "uuid, kit, elo, wins, losses, streak, best_streak",
                        "?, ?, ?, ?, ?, ?, ?",
                        "uuid, kit",
                        "elo = ?, wins = ?, losses = ?, streak = ?, best_streak = ?"),
                stats.uuid().toString(), stats.kit(), stats.elo(), stats.wins(), stats.losses(),
                stats.streak(), stats.bestStreak(),
                stats.elo(), stats.wins(), stats.losses(), stats.streak(), stats.bestStreak());
    }

    /** Unranked result: the record moves, the rating does not. */
    public void recordCasual(UUID winner, UUID loser, String kit) {
        KitStats winnerStats = get(winner, kit);
        KitStats loserStats = get(loser, kit);
        winnerStats.won(0);
        loserStats.lost(0);
        save(winnerStats);
        save(loserStats);
    }

    public CompletableFuture<List<KitStats>> leaderboard(String kit, int limit) {
        return database.queryListAsync(
                "SELECT * FROM practice_stats WHERE kit = ? ORDER BY elo DESC LIMIT ?",
                StatsService::map, kit, limit);
    }

    public CompletableFuture<List<KitStats>> statsOf(UUID uuid) {
        return database.queryListAsync("SELECT * FROM practice_stats WHERE uuid = ?",
                StatsService::map, uuid.toString());
    }

    public void load(UUID uuid) {
        Map<String, KitStats> own = new ConcurrentHashMap<>();
        for (KitStats stats : database.queryList("SELECT * FROM practice_stats WHERE uuid = ?",
                StatsService::map, uuid.toString())) {
            own.put(stats.kit(), stats);
        }
        cache.put(uuid, own);
    }

    public void unload(UUID uuid) {
        Map<String, KitStats> own = cache.remove(uuid);
        if (own != null) {
            own.values().forEach(this::save);
        }
    }

    public void saveAll() {
        for (Map<String, KitStats> own : cache.values()) {
            own.values().forEach(this::save);
        }
    }

    /** Kits the player has a record in, best rating first. */
    public List<KitStats> sortedOf(UUID uuid) {
        List<KitStats> stats = new ArrayList<>(allOf(uuid).values());
        stats.sort((a, b) -> Integer.compare(b.elo(), a.elo()));
        return stats;
    }

    private static KitStats map(ResultSet results) throws SQLException {
        return new KitStats(
                UUID.fromString(results.getString("uuid")),
                results.getString("kit"),
                results.getInt("elo"),
                results.getInt("wins"),
                results.getInt("losses"),
                results.getInt("streak"),
                results.getInt("best_streak"));
    }
}

package me.alpha432.network.core.profile;

import me.alpha432.network.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads a {@link PlayerProfile} before the player is let in, keeps it cached while they are
 * online and writes it back on quit. Everything else in the network reads profiles from here.
 */
public final class ProfileService implements Listener {

    private static final String SELECT = "SELECT * FROM profiles WHERE uuid = ?";

    private final Plugin plugin;
    private final Database database;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final double startingBalance;
    private final String defaultRank;

    public ProfileService(Plugin plugin, Database database, double startingBalance, String defaultRank) {
        this.plugin = plugin;
        this.database = database;
        this.startingBalance = startingBalance;
        this.defaultRank = defaultRank;
    }

    /** Saves every dirty profile in the cache; called by the autosave task and on shutdown. */
    public void saveAll() {
        for (PlayerProfile profile : cache.values()) {
            if (profile.isDirty()) {
                writeBlocking(profile);
            }
        }
    }

    public PlayerProfile get(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerProfile get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public Collection<PlayerProfile> cached() {
        return cache.values();
    }

    public Optional<PlayerProfile> byName(String name) {
        for (PlayerProfile profile : cache.values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    /** Cache first, database second. Never call this on the main thread. */
    public PlayerProfile loadBlocking(UUID uuid, String fallbackName) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        PlayerProfile stored = database.queryFirst(SELECT, ProfileService::map, uuid.toString());
        return stored != null ? stored : PlayerProfile.fresh(uuid, fallbackName, startingBalance, defaultRank);
    }

    /** Resolves an offline player by name. Returns {@code null} when unknown. */
    public CompletableFuture<PlayerProfile> lookup(String name) {
        Optional<PlayerProfile> online = byName(name);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get());
        }
        return database.supplyAsync(() -> database.queryFirst(
                "SELECT * FROM profiles WHERE LOWER(name) = LOWER(?)", ProfileService::map, name));
    }

    public CompletableFuture<PlayerProfile> lookup(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.supplyAsync(() -> database.queryFirst(SELECT, ProfileService::map, uuid.toString()));
    }

    public CompletableFuture<List<PlayerProfile>> topBalances(int limit) {
        return database.queryListAsync(
                "SELECT * FROM profiles ORDER BY balance DESC LIMIT ?", ProfileService::map, limit);
    }

    /** Writes a profile off the main thread. */
    public CompletableFuture<Void> save(PlayerProfile profile) {
        return database.runAsync(() -> writeBlocking(profile));
    }

    public void writeBlocking(PlayerProfile profile) {
        database.execute(database.upsert("profiles",
                        "uuid, name, first_join, last_seen, balance, rank_id, play_time",
                        "?, ?, ?, ?, ?, ?, ?",
                        "uuid",
                        "name = ?, last_seen = ?, balance = ?, rank_id = ?, play_time = ?"),
                profile.uuid().toString(), profile.name(), profile.firstJoin(), profile.lastSeen(),
                profile.balance(), profile.rankId(), profile.playTime(),
                profile.name(), profile.lastSeen(), profile.balance(), profile.rankId(), profile.playTime());
        profile.markClean();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            PlayerProfile profile = loadBlocking(event.getUniqueId(), event.getName());
            profile.name(event.getName());
            cache.put(event.getUniqueId(), profile);
            sessionStart.put(event.getUniqueId(), System.currentTimeMillis());
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Could not load profile for " + event.getName() + ": " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    net.kyori.adventure.text.Component.text("Could not load your profile, please try again."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerProfile profile = cache.remove(uuid);
        Long start = sessionStart.remove(uuid);
        if (profile == null) {
            return;
        }
        if (start != null) {
            profile.addPlayTime(System.currentTimeMillis() - start);
        }
        profile.lastSeen(System.currentTimeMillis());
        save(profile);
    }

    /** Drops cached profiles of players that are no longer online (e.g. a denied login). */
    public void pruneOffline() {
        List<UUID> stale = new ArrayList<>();
        for (UUID uuid : cache.keySet()) {
            if (Bukkit.getPlayer(uuid) == null) {
                stale.add(uuid);
            }
        }
        for (UUID uuid : stale) {
            PlayerProfile profile = cache.remove(uuid);
            sessionStart.remove(uuid);
            if (profile != null && profile.isDirty()) {
                save(profile);
            }
        }
    }

    private static PlayerProfile map(java.sql.ResultSet results) throws java.sql.SQLException {
        return new PlayerProfile(
                UUID.fromString(results.getString("uuid")),
                results.getString("name"),
                results.getLong("first_join"),
                results.getLong("last_seen"),
                results.getDouble("balance"),
                results.getString("rank_id"),
                results.getLong("play_time"));
    }
}

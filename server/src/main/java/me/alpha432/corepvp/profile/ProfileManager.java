package me.alpha432.corepvp.profile;

import me.alpha432.corepvp.storage.Database;
import me.alpha432.corepvp.storage.ProfileRepository;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Cache of online players' profiles, plus the autosave loop. */
public final class ProfileManager {

    private final Plugin plugin;
    private final Database database;
    private final ProfileRepository repository;
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();

    private BukkitTask autosaveTask;
    private me.alpha432.corepvp.kit.layout.KitLayoutService layouts;
    private me.alpha432.corepvp.survival.SurvivalService survival;

    public ProfileManager(Plugin plugin, Database database, ProfileRepository repository) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
    }

    /**
     * Loads a profile on the calling thread. Only ever called from
     * {@code AsyncPlayerPreLoginEvent}, which is already off the main thread.
     */
    public Profile loadBlocking(UUID uuid, String name) throws SQLException {
        try (Connection connection = database.connection()) {
            Profile profile = repository.load(connection, uuid, name);
            if (layouts != null) {
                // Same connection, same login: one round trip instead of two.
                layouts.load(connection, uuid);
            }
            if (survival != null) {
                survival.loadHomes(connection, uuid);
            }
            cache.put(uuid, profile);
            return profile;
        }
    }

    public void layouts(me.alpha432.corepvp.kit.layout.KitLayoutService layouts) {
        this.layouts = layouts;
    }

    public void survival(me.alpha432.corepvp.survival.SurvivalService survival) {
        this.survival = survival;
    }

    public Profile get(UUID uuid) {
        return cache.get(uuid);
    }

    public Profile get(Player player) {
        return cache.get(player.getUniqueId());
    }

    /**
     * The profile for an online player, creating a blank one if the login load
     * somehow did not happen. Never returns null, so callers in gameplay paths
     * do not need null checks.
     */
    public Profile require(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> {
            plugin.getLogger().warning("Profile for " + player.getName()
                    + " was missing at use time; using an unsaved placeholder.");
            Profile profile = new Profile(uuid, player.getName());
            profile.fresh(true);
            return profile;
        });
    }

    public Collection<Profile> cached() {
        return cache.values();
    }

    public void saveAsync(Profile profile) {
        profile.clearDirty();
        database.execute(connection -> repository.save(connection, profile))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to save profile of " + profile.name(), throwable);
                    profile.markDirty();
                    return null;
                });
    }

    /** Saves and drops a profile from the cache (on quit). */
    public void unload(UUID uuid) {
        if (layouts != null) {
            layouts.unload(uuid);
        }
        Profile profile = cache.remove(uuid);
        if (profile != null) {
            profile.lastSeen(System.currentTimeMillis());
            saveAsync(profile);
        }
    }

    public void startAutosave(int intervalSeconds) {
        if (intervalSeconds <= 0) {
            return;
        }
        long ticks = intervalSeconds * 20L;
        autosaveTask = Tasks.timer(() -> {
            List<Profile> dirty = new ArrayList<>();
            for (Profile profile : cache.values()) {
                if (profile.dirty()) {
                    dirty.add(profile);
                }
            }
            dirty.forEach(this::saveAsync);
        }, ticks, ticks);
    }

    /**
     * Writes every cached profile on the calling thread. Used during shutdown,
     * where the Bukkit scheduler is already gone and async saves would be lost.
     */
    public void saveAllBlocking() {
        if (cache.isEmpty()) {
            return;
        }
        List<Profile> profiles = new ArrayList<>(cache.values());
        database.executeBlocking(connection -> {
            for (Profile profile : profiles) {
                repository.save(connection, profile);
                profile.clearDirty();
            }
        });
        plugin.getLogger().info("Saved " + profiles.size() + " profile(s) on shutdown.");
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        saveAllBlocking();
        cache.clear();
    }
}

package me.alpha432.network.smp.home;

import me.alpha432.network.core.rank.Permissions;
import me.alpha432.network.core.storage.Database;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player homes. Loaded with the player and written through on every change, so a crash never
 * costs more than the last command.
 */
public final class HomeService implements Listener {

    /** How many homes a player without any rank perk gets. */
    public static final String HOMES_PERMISSION = "network.smp.homes.";

    private final Plugin plugin;
    private final Database database;
    private final Map<UUID, Map<String, Location>> cache = new ConcurrentHashMap<>();
    private final int defaultLimit;

    public HomeService(Plugin plugin, Database database, int defaultLimit) {
        this.plugin = plugin;
        this.database = database;
        this.defaultLimit = defaultLimit;
    }

    public int limit(Player player) {
        return Permissions.highest(player, HOMES_PERMISSION, defaultLimit);
    }

    public Map<String, Location> homes(Player player) {
        return cache.getOrDefault(player.getUniqueId(), Map.of());
    }

    public Location home(Player player, String name) {
        return homes(player).get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names(Player player) {
        return new ArrayList<>(homes(player).keySet());
    }

    /** @return false when the player already reached their limit. */
    public boolean set(Player player, String name, Location location) {
        String key = name.toLowerCase(Locale.ROOT);
        Map<String, Location> homes = cache.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>());
        if (!homes.containsKey(key) && homes.size() >= limit(player)) {
            return false;
        }
        homes.put(key, location.clone());
        database.executeAsync(database.upsert("smp_homes",
                        "uuid, name, location", "?, ?, ?", "uuid, name", "location = ?"),
                player.getUniqueId().toString(), key, Locations.serialize(location),
                Locations.serialize(location));
        return true;
    }

    public boolean delete(Player player, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Map<String, Location> homes = cache.get(player.getUniqueId());
        if (homes == null || homes.remove(key) == null) {
            return false;
        }
        database.executeAsync("DELETE FROM smp_homes WHERE uuid = ? AND name = ?",
                player.getUniqueId().toString(), key);
        return true;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Map<String, Location> homes = new LinkedHashMap<>();
        for (String[] row : database.queryList(
                "SELECT name, location FROM smp_homes WHERE uuid = ?",
                results -> new String[]{results.getString("name"), results.getString("location")},
                event.getUniqueId().toString())) {
            Location location = Locations.deserialize(row[1]);
            if (location != null) {
                homes.put(row[0], location);
            } else {
                // The world was removed from the server; keep the row but skip the entry.
                plugin.getLogger().fine("Skipping home " + row[0] + " of " + event.getName()
                        + ": world is not loaded");
            }
        }
        cache.put(event.getUniqueId(), homes);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    /** Used by the SMP scoreboard to show how many homes are still free. */
    public String usage(Player player) {
        return homes(player).size() + "/" + limit(player);
    }
}

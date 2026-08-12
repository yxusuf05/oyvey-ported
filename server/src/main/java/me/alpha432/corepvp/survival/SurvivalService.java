package me.alpha432.corepvp.survival;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.KitSerializer;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.util.Cooldown;
import me.alpha432.corepvp.util.Locations;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The persistent survival world.
 *
 * <p>Unlike every other mode, survival inventories belong to the player and
 * must survive a restart, so they go to the database rather than being kept in
 * memory by {@link me.alpha432.corepvp.state.PlayerStateService}.
 */
public final class SurvivalService {

    private static final String SELECT_STATE =
            "SELECT * FROM corepvp_survival WHERE uuid = ?";
    private static final String UPDATE_STATE = """
            UPDATE corepvp_survival SET contents = ?, armor = ?, off_hand = ?,
              health = ?, food = ?, level = ?, exp = ?, location = ?
            WHERE uuid = ?
            """;
    private static final String INSERT_STATE = """
            INSERT INTO corepvp_survival
              (uuid, contents, armor, off_hand, health, food, level, exp, location)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_HOMES =
            "SELECT name, location FROM corepvp_homes WHERE uuid = ?";
    private static final String UPDATE_HOME =
            "UPDATE corepvp_homes SET location = ? WHERE uuid = ? AND name = ?";
    private static final String INSERT_HOME =
            "INSERT INTO corepvp_homes (uuid, name, location) VALUES (?, ?, ?)";
    private static final String DELETE_HOME =
            "DELETE FROM corepvp_homes WHERE uuid = ? AND name = ?";

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Map<UUID, Map<String, Location>> homes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> teleportRequests = new ConcurrentHashMap<>();
    private final Cooldown requestCooldown = new Cooldown();

    public SurvivalService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    // ------------------------------------------------------------------
    //  Entering and leaving
    // ------------------------------------------------------------------

    public boolean enter(Player player) {
        if (plugin.worlds().survival() == null) {
            messages.send(player, "survival.disabled");
            return false;
        }
        if (plugin.matches().matchOf(player) != null) {
            messages.send(player, "survival.in-match");
            return false;
        }
        if (plugin.queues().inQueue(player)) {
            plugin.queues().leave(player);
        }
        if (plugin.ffa().isPlaying(player)) {
            plugin.ffa().leave(player);
        }

        plugin.states().set(player, PlayerState.SURVIVAL);
        plugin.database().query(connection -> load(connection, player.getUniqueId()))
                .thenAccept(state -> me.alpha432.corepvp.util.Tasks.sync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Location target = state == null || state.location() == null
                            ? spawn()
                            : state.location();
                    player.teleport(target == null ? spawn() : target);
                    if (state != null) {
                        state.applyTo(player);
                    }
                    messages.send(player, "survival.entered");
                }))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Could not load survival data", throwable);
                    return null;
                });
        return true;
    }

    /** Writes the player's survival state back before they go somewhere else. */
    public void exit(Player player, boolean toLobby) {
        saveState(player);
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
            messages.send(player, "survival.left");
        }
    }

    public void saveState(Player player) {
        if (!plugin.states().is(player, PlayerState.SURVIVAL)) {
            return;
        }
        SurvivalState state = SurvivalState.capture(player);
        plugin.database().execute(connection -> store(connection, player.getUniqueId(), state))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Could not save survival data", throwable);
                    return null;
                });
    }

    /** Shutdown path: blocking so nothing is lost when the scheduler is gone. */
    public void saveAllBlocking() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.states().is(player, PlayerState.SURVIVAL)) {
                continue;
            }
            SurvivalState state = SurvivalState.capture(player);
            plugin.database().executeBlocking(connection -> store(connection, player.getUniqueId(), state));
        }
    }

    public Location spawn() {
        Location configured = Locations.deserialize(
                plugin.configs().main().getString("survival.spawn"));
        if (configured != null) {
            return configured;
        }
        return plugin.worlds().survival() == null ? null : plugin.worlds().survival().getSpawnLocation();
    }

    public void setSpawn(Location location) {
        plugin.configs().main().set("survival.spawn", Locations.serialize(location));
        plugin.configs().file("config.yml").save();
    }

    public boolean inSpawnProtection(Location location) {
        Location spawn = spawn();
        if (spawn == null || location.getWorld() == null || spawn.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().equals(spawn.getWorld())) {
            return false;
        }
        double radius = plugin.configs().main().getDouble("survival.spawn-protection-radius", 24.0D);
        return location.distanceSquared(spawn) <= radius * radius;
    }

    // ------------------------------------------------------------------
    //  Homes
    // ------------------------------------------------------------------

    public void loadHomes(Connection connection, UUID uuid) throws SQLException {
        Map<String, Location> byName = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_HOMES)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Location location = Locations.deserialize(result.getString("location"));
                    if (location != null) {
                        byName.put(result.getString("name").toLowerCase(Locale.ROOT), location);
                    }
                }
            }
        }
        homes.put(uuid, byName);
    }

    public void unload(UUID uuid) {
        homes.remove(uuid);
        teleportRequests.remove(uuid);
        teleportRequests.values().remove(uuid);
    }

    public Map<String, Location> homes(Player player) {
        return homes.getOrDefault(player.getUniqueId(), Map.of());
    }

    public int homeLimit(Player player) {
        int limit = plugin.configs().main().getInt("survival.default-homes", 2);
        for (int candidate = 20; candidate >= limit; candidate--) {
            if (player.hasPermission("corepvp.homes." + candidate)) {
                return candidate;
            }
        }
        return limit;
    }

    public boolean setHome(Player player, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Map<String, Location> byName = homes.computeIfAbsent(player.getUniqueId(),
                uuid -> new ConcurrentHashMap<>());
        if (!byName.containsKey(key) && byName.size() >= homeLimit(player)) {
            messages.send(player, "survival.home-limit", Messages.of("limit", homeLimit(player)));
            return false;
        }

        Location location = player.getLocation();
        byName.put(key, location);
        String serialized = Locations.serialize(location);

        plugin.database().execute(connection -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_HOME)) {
                statement.setString(1, serialized);
                statement.setString(2, player.getUniqueId().toString());
                statement.setString(3, key);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(INSERT_HOME)) {
                    statement.setString(1, player.getUniqueId().toString());
                    statement.setString(2, key);
                    statement.setString(3, serialized);
                    statement.executeUpdate();
                }
            }
        });
        return true;
    }

    public boolean deleteHome(Player player, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Map<String, Location> byName = homes.get(player.getUniqueId());
        if (byName == null || byName.remove(key) == null) {
            return false;
        }
        plugin.database().execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_HOME)) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, key);
                statement.executeUpdate();
            }
        });
        return true;
    }

    // ------------------------------------------------------------------
    //  Teleport requests
    // ------------------------------------------------------------------

    public void requestTeleport(Player from, Player target) {
        if (from.equals(target)) {
            messages.send(from, "survival.tpa-self");
            return;
        }
        long cooldown = plugin.configs().main().getLong("survival.tpa-cooldown-seconds", 10L) * 1000L;
        if (!requestCooldown.tryUse(from.getUniqueId(), cooldown)) {
            messages.send(from, "survival.tpa-cooldown", Messages.of("time",
                    me.alpha432.corepvp.util.TimeUtil.seconds(
                            requestCooldown.remainingMillis(from.getUniqueId()))));
            return;
        }
        teleportRequests.put(target.getUniqueId(), from.getUniqueId());
        messages.send(from, "survival.tpa-sent", Messages.of("player", target.getName()));
        messages.send(target, "survival.tpa-received", Messages.of("player", from.getName()));
    }

    public void acceptTeleport(Player target) {
        UUID fromId = teleportRequests.remove(target.getUniqueId());
        Player from = fromId == null ? null : plugin.getServer().getPlayer(fromId);
        if (from == null || !from.isOnline()) {
            messages.send(target, "survival.tpa-none");
            return;
        }
        if (!plugin.states().is(from, PlayerState.SURVIVAL)
                || !plugin.states().is(target, PlayerState.SURVIVAL)) {
            messages.send(target, "survival.tpa-not-survival");
            return;
        }
        from.teleport(target.getLocation());
        messages.send(from, "survival.tpa-accepted", Messages.of("player", target.getName()));
        messages.send(target, "survival.tpa-accepted-target", Messages.of("player", from.getName()));
    }

    // ------------------------------------------------------------------
    //  Persistence helpers
    // ------------------------------------------------------------------

    private SurvivalState load(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STATE)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new SurvivalState(
                        KitSerializer.decode(result.getString("contents")),
                        KitSerializer.decode(result.getString("armor")),
                        first(KitSerializer.decode(result.getString("off_hand"))),
                        result.getDouble("health"),
                        result.getInt("food"),
                        result.getInt("level"),
                        (float) result.getDouble("exp"),
                        Locations.deserialize(result.getString("location")));
            }
        }
    }

    private void store(Connection connection, UUID uuid, SurvivalState state) throws SQLException {
        String contents = KitSerializer.encode(state.contents());
        String armor = KitSerializer.encode(state.armor());
        String offHand = KitSerializer.encode(new ItemStack[]{state.offHand()});
        String location = state.location() == null ? null : Locations.serialize(state.location());

        int updated;
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_STATE)) {
            statement.setString(1, contents);
            statement.setString(2, armor);
            statement.setString(3, offHand);
            statement.setDouble(4, state.health());
            statement.setInt(5, state.food());
            statement.setInt(6, state.level());
            statement.setDouble(7, state.exp());
            statement.setString(8, location);
            statement.setString(9, uuid.toString());
            updated = statement.executeUpdate();
        }
        if (updated == 0) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_STATE)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, contents);
                statement.setString(3, armor);
                statement.setString(4, offHand);
                statement.setDouble(5, state.health());
                statement.setInt(6, state.food());
                statement.setInt(7, state.level());
                statement.setDouble(8, state.exp());
                statement.setString(9, location);
                statement.executeUpdate();
            }
        }
    }

    private static ItemStack first(ItemStack[] items) {
        return items.length == 0 ? null : items[0];
    }
}

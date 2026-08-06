package me.alpha432.network.staff.punishment;

import me.alpha432.network.core.storage.Database;
import org.bukkit.plugin.Plugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores every punishment and answers "is this player banned or muted right now".
 *
 * <p>Active mutes of online players are cached so the chat listener never touches the database.
 */
public final class PunishmentService {

    private final Plugin plugin;
    private final Database database;
    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();

    public PunishmentService(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void add(Punishment punishment) {
        database.execute(
                "INSERT INTO staff_punishments "
                        + "(id, uuid, name, type, reason, actor, created_at, expires_at, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                punishment.id(), punishment.target().toString(), punishment.targetName(),
                punishment.type().name(), punishment.reason(), punishment.actor(),
                punishment.createdAt(), punishment.expiresAt(), punishment.active() ? 1 : 0);
        if (punishment.type() == PunishmentType.MUTE && punishment.isInEffect()) {
            activeMutes.put(punishment.target(), punishment);
        }
    }

    public CompletableFuture<Void> addAsync(Punishment punishment) {
        if (punishment.type() == PunishmentType.MUTE && punishment.isInEffect()) {
            activeMutes.put(punishment.target(), punishment);
        }
        return database.runAsync(() -> add(punishment));
    }

    /** The punishment blocking the player right now, or {@code null}. */
    public Punishment activeOf(UUID target, PunishmentType type) {
        if (type == PunishmentType.MUTE) {
            Punishment cached = activeMutes.get(target);
            if (cached != null) {
                if (cached.isInEffect()) {
                    return cached;
                }
                activeMutes.remove(target);
                return null;
            }
        }
        Punishment found = database.queryFirst(
                "SELECT * FROM staff_punishments WHERE uuid = ? AND type = ? AND active = 1 "
                        + "ORDER BY created_at DESC",
                PunishmentService::map, target.toString(), type.name());
        if (found == null || !found.isInEffect()) {
            return null;
        }
        if (type == PunishmentType.MUTE) {
            activeMutes.put(target, found);
        }
        return found;
    }

    /** Loads the active mute of a joining player into the cache. */
    public void cacheMute(UUID target) {
        Punishment mute = activeOf(target, PunishmentType.MUTE);
        if (mute == null) {
            activeMutes.remove(target);
        }
    }

    public void forget(UUID target) {
        activeMutes.remove(target);
    }

    /**
     * Lifts every active punishment of a type.
     *
     * @return how many entries were changed
     */
    public int lift(UUID target, PunishmentType type) {
        int changed = database.execute(
                "UPDATE staff_punishments SET active = 0 WHERE uuid = ? AND type = ? AND active = 1",
                target.toString(), type.name());
        if (type == PunishmentType.MUTE) {
            activeMutes.remove(target);
        }
        return changed;
    }

    public List<Punishment> history(UUID target, int limit) {
        return database.queryList(
                "SELECT * FROM staff_punishments WHERE uuid = ? ORDER BY created_at DESC LIMIT ?",
                PunishmentService::map, target.toString(), limit);
    }

    public CompletableFuture<List<Punishment>> historyAsync(UUID target, int limit) {
        return database.supplyAsync(() -> history(target, limit));
    }

    public CompletableFuture<List<Punishment>> activeBans(int limit) {
        return database.supplyAsync(() -> database.queryList(
                "SELECT * FROM staff_punishments WHERE type = ? AND active = 1 "
                        + "ORDER BY created_at DESC LIMIT ?",
                PunishmentService::map, PunishmentType.BAN.name(), limit));
    }

    public CompletableFuture<Integer> clear(UUID target) {
        return database.supplyAsync(() -> {
            activeMutes.remove(target);
            return database.execute("DELETE FROM staff_punishments WHERE uuid = ?", target.toString());
        });
    }

    /** Drops rows whose ban or mute ran out, so /banlist stays honest. */
    public void expireOutdated() {
        long now = System.currentTimeMillis();
        int changed = database.execute(
                "UPDATE staff_punishments SET active = 0 "
                        + "WHERE active = 1 AND expires_at > 0 AND expires_at <= ?", now);
        if (changed > 0) {
            plugin.getLogger().info("Expired " + changed + " punishment(s).");
        }
        activeMutes.values().removeIf(punishment -> !punishment.isInEffect());
    }

    private static Punishment map(ResultSet results) throws SQLException {
        return new Punishment(
                results.getString("id"),
                UUID.fromString(results.getString("uuid")),
                results.getString("name"),
                PunishmentType.valueOf(results.getString("type")),
                results.getString("reason"),
                results.getString("actor"),
                results.getLong("created_at"),
                results.getLong("expires_at"),
                results.getInt("active") == 1);
    }
}

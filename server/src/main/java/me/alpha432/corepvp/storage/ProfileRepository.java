package me.alpha432.corepvp.storage;

import me.alpha432.corepvp.profile.KitStats;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.profile.ProfileSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Reads and writes profiles.
 *
 * <p>Writes are UPDATE-then-INSERT rather than an upsert, because SQLite's
 * {@code ON CONFLICT} and MySQL's {@code ON DUPLICATE KEY} have incompatible
 * syntax and one portable path is worth more here than one round trip.
 */
public final class ProfileRepository {

    private static final String SELECT_PROFILE =
            "SELECT * FROM corepvp_profiles WHERE uuid = ?";
    private static final String SELECT_KIT_STATS =
            "SELECT * FROM corepvp_kit_stats WHERE uuid = ?";
    private static final String UPDATE_PROFILE = """
            UPDATE corepvp_profiles SET
              name = ?, rank_id = ?, last_seen = ?, global_kills = ?, global_deaths = ?,
              opt_scoreboard = ?, opt_duel_requests = ?, opt_party_invites = ?,
              opt_player_visible = ?, opt_allow_spectate = ?
            WHERE uuid = ?
            """;
    private static final String INSERT_PROFILE = """
            INSERT INTO corepvp_profiles
              (uuid, name, rank_id, first_seen, last_seen, global_kills, global_deaths,
               opt_scoreboard, opt_duel_requests, opt_party_invites,
               opt_player_visible, opt_allow_spectate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_KIT_STATS = """
            UPDATE corepvp_kit_stats SET elo = ?, wins = ?, losses = ?, kills = ?, deaths = ?
            WHERE uuid = ? AND kit = ?
            """;
    private static final String INSERT_KIT_STATS = """
            INSERT INTO corepvp_kit_stats (uuid, kit, elo, wins, losses, kills, deaths)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final Database database;

    public ProfileRepository(Database database) {
        this.database = database;
    }

    public Database database() {
        return database;
    }

    /** Loads a profile, or returns a brand new one if the player is unknown. */
    public Profile load(Connection connection, UUID uuid, String name) throws SQLException {
        Profile profile = new Profile(uuid, name);

        try (PreparedStatement statement = connection.prepareStatement(SELECT_PROFILE)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    profile.fresh(false);
                    profile.name(result.getString("name"));
                    profile.rankId(result.getString("rank_id"));
                    profile.firstSeen(result.getLong("first_seen"));
                    profile.lastSeen(result.getLong("last_seen"));
                    profile.globalKills(result.getInt("global_kills"));
                    profile.globalDeaths(result.getInt("global_deaths"));
                    for (ProfileSettings.Flag flag : ProfileSettings.Flag.values()) {
                        profile.settings().set(flag, result.getInt(flag.column()) != 0);
                    }
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(SELECT_KIT_STATS)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    KitStats stats = profile.kit(result.getString("kit"));
                    stats.elo(result.getInt("elo"));
                    stats.wins(result.getInt("wins"));
                    stats.losses(result.getInt("losses"));
                    stats.kills(result.getInt("kills"));
                    stats.deaths(result.getInt("deaths"));
                }
            }
        }

        // The name may have changed since the last login, and lastSeen is always
        // stale on load; both flip the dirty flag, which we do not want to carry
        // into "has unsaved gameplay changes".
        profile.clearDirty();
        return profile;
    }

    public void save(Connection connection, Profile profile) throws SQLException {
        int updated;
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE)) {
            int index = 1;
            statement.setString(index++, profile.name());
            statement.setString(index++, profile.rankId());
            statement.setLong(index++, profile.lastSeen());
            statement.setInt(index++, profile.globalKills());
            statement.setInt(index++, profile.globalDeaths());
            for (ProfileSettings.Flag flag : ProfileSettings.Flag.values()) {
                statement.setInt(index++, profile.settings().get(flag) ? 1 : 0);
            }
            statement.setString(index, profile.uuid().toString());
            updated = statement.executeUpdate();
        }

        if (updated == 0) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_PROFILE)) {
                int index = 1;
                statement.setString(index++, profile.uuid().toString());
                statement.setString(index++, profile.name());
                statement.setString(index++, profile.rankId());
                statement.setLong(index++, profile.firstSeen());
                statement.setLong(index++, profile.lastSeen());
                statement.setInt(index++, profile.globalKills());
                statement.setInt(index++, profile.globalDeaths());
                for (ProfileSettings.Flag flag : ProfileSettings.Flag.values()) {
                    statement.setInt(index++, profile.settings().get(flag) ? 1 : 0);
                }
                statement.executeUpdate();
            }
        }

        saveKitStats(connection, profile);
    }

    private void saveKitStats(Connection connection, Profile profile) throws SQLException {
        for (KitStats stats : profile.allKitStats()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_KIT_STATS)) {
                statement.setInt(1, stats.elo());
                statement.setInt(2, stats.wins());
                statement.setInt(3, stats.losses());
                statement.setInt(4, stats.kills());
                statement.setInt(5, stats.deaths());
                statement.setString(6, profile.uuid().toString());
                statement.setString(7, stats.kit());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(INSERT_KIT_STATS)) {
                    statement.setString(1, profile.uuid().toString());
                    statement.setString(2, stats.kit());
                    statement.setInt(3, stats.elo());
                    statement.setInt(4, stats.wins());
                    statement.setInt(5, stats.losses());
                    statement.setInt(6, stats.kills());
                    statement.setInt(7, stats.deaths());
                    statement.executeUpdate();
                }
            }
        }
    }
}

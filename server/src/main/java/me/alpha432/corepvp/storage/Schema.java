package me.alpha432.corepvp.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Table definitions.
 *
 * <p>The DDL is deliberately written in the intersection of SQLite and MySQL
 * syntax - SQLite treats column types as affinities, so {@code VARCHAR(36)} and
 * {@code BIGINT} are accepted by both and one statement list serves both
 * backends.
 */
final class Schema {

    private Schema() {
    }

    private static final List<String> TABLES = List.of("""
            CREATE TABLE IF NOT EXISTS corepvp_profiles (
              uuid                VARCHAR(36)  NOT NULL,
              name                VARCHAR(16)  NOT NULL,
              rank_id             VARCHAR(32)  NOT NULL DEFAULT 'default',
              first_seen          BIGINT       NOT NULL,
              last_seen           BIGINT       NOT NULL,
              global_kills        INT          NOT NULL DEFAULT 0,
              global_deaths       INT          NOT NULL DEFAULT 0,
              opt_scoreboard      INT          NOT NULL DEFAULT 1,
              opt_duel_requests   INT          NOT NULL DEFAULT 1,
              opt_party_invites   INT          NOT NULL DEFAULT 1,
              opt_player_visible  INT          NOT NULL DEFAULT 1,
              opt_allow_spectate  INT          NOT NULL DEFAULT 1,
              PRIMARY KEY (uuid)
            )
            """, """
            CREATE TABLE IF NOT EXISTS corepvp_kit_stats (
              uuid     VARCHAR(36) NOT NULL,
              kit      VARCHAR(32) NOT NULL,
              elo      INT         NOT NULL DEFAULT 1000,
              wins     INT         NOT NULL DEFAULT 0,
              losses   INT         NOT NULL DEFAULT 0,
              kills    INT         NOT NULL DEFAULT 0,
              deaths   INT         NOT NULL DEFAULT 0,
              PRIMARY KEY (uuid, kit)
            )
            """, """
            CREATE TABLE IF NOT EXISTS corepvp_survival (
              uuid      VARCHAR(36) NOT NULL,
              contents  MEDIUMTEXT,
              armor     MEDIUMTEXT,
              off_hand  MEDIUMTEXT,
              health    DOUBLE      NOT NULL DEFAULT 20,
              food      INT         NOT NULL DEFAULT 20,
              level     INT         NOT NULL DEFAULT 0,
              exp       DOUBLE      NOT NULL DEFAULT 0,
              location  VARCHAR(128),
              PRIMARY KEY (uuid)
            )
            """, """
            CREATE TABLE IF NOT EXISTS corepvp_homes (
              uuid     VARCHAR(36) NOT NULL,
              name     VARCHAR(32) NOT NULL,
              location VARCHAR(128) NOT NULL,
              PRIMARY KEY (uuid, name)
            )
            """, """
            CREATE TABLE IF NOT EXISTS corepvp_kit_layouts (
              uuid     VARCHAR(36) NOT NULL,
              kit      VARCHAR(32) NOT NULL,
              slot     INT         NOT NULL,
              label    VARCHAR(32) NOT NULL,
              contents MEDIUMTEXT,
              PRIMARY KEY (uuid, kit, slot)
            )
            """);

    private static final List<String> INDEXES = List.of(
            "CREATE INDEX IF NOT EXISTS idx_corepvp_profiles_name ON corepvp_profiles (name)",
            "CREATE INDEX IF NOT EXISTS idx_corepvp_kit_stats_elo ON corepvp_kit_stats (kit, elo)");

    static void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : TABLES) {
                statement.executeUpdate(table);
            }
            for (String index : INDEXES) {
                statement.executeUpdate(index);
            }
        }
    }
}

package me.alpha432.network.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Connection pool plus a few convenience wrappers. SQLite ships inside the jar; MySQL is used
 * when a driver is available on the classpath, otherwise the server falls back to SQLite instead
 * of refusing to start.
 */
public final class Database implements AutoCloseable {

    private final Plugin plugin;
    private final HikariDataSource source;
    private final ExecutorService executor;
    private final boolean sqlite;

    public Database(Plugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        boolean wantsMysql = "mysql".equalsIgnoreCase(config.getString("type", "sqlite"));
        if (wantsMysql && !driverPresent("com.mysql.cj.jdbc.Driver")) {
            plugin.getLogger().warning("storage.type is mysql but no MySQL driver was found on the "
                    + "classpath. Drop mysql-connector-j into the server's libraries folder or switch "
                    + "back to sqlite. Falling back to SQLite for now.");
            wantsMysql = false;
        }
        this.sqlite = !wantsMysql;

        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(sqlite ? 1 : 4, runnable -> {
            Thread thread = new Thread(runnable, "NetworkCore-DB-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("NetworkCore");
        if (sqlite) {
            File file = new File(plugin.getDataFolder(), config.getString("file", "data.db"));
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite serialises writes anyway, so a single connection avoids lock contention.
            hikari.setMaximumPoolSize(1);
        } else {
            ConfigurationSection mysql = config.getConfigurationSection("mysql");
            String host = mysql == null ? "localhost" : mysql.getString("host", "localhost");
            int port = mysql == null ? 3306 : mysql.getInt("port", 3306);
            String database = mysql == null ? "network" : mysql.getString("database", "network");
            String properties = mysql == null ? "" : mysql.getString("properties", "");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + properties);
            hikari.setUsername(mysql == null ? "root" : mysql.getString("user", "root"));
            hikari.setPassword(mysql == null ? "" : mysql.getString("password", ""));
            hikari.setMaximumPoolSize(config.getInt("pool-size", 8));
        }
        hikari.setConnectionTimeout(config.getLong("connection-timeout", 10_000L));
        this.source = new HikariDataSource(hikari);

        if (sqlite) {
            // PRAGMA statements answer with a result set, so executeUpdate would fail here.
            executeRaw("PRAGMA journal_mode=WAL");
            executeRaw("PRAGMA synchronous=NORMAL");
        }
    }

    /** Runs a statement that may or may not produce a result set (PRAGMA, DDL). */
    public void executeRaw(String sql) {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed statement: " + sql, e);
        }
    }

    public boolean isSqlite() {
        return sqlite;
    }

    public Connection connection() throws SQLException {
        return source.getConnection();
    }

    /** Runs an INSERT/UPDATE/DDL statement and returns the affected row count. */
    public int execute(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed statement: " + sql, e);
            return 0;
        }
    }

    public CompletableFuture<Integer> executeAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> execute(sql, params), executor);
    }

    /** Maps the first row, or returns {@code null} when the query found nothing. */
    public <T> T queryFirst(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? mapper.map(results) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed query: " + sql, e);
            return null;
        }
    }

    public <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    rows.add(mapper.map(results));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed query: " + sql, e);
        }
        return rows;
    }

    public <T> CompletableFuture<List<T>> queryListAsync(String sql, RowMapper<T> mapper, Object... params) {
        return CompletableFuture.supplyAsync(() -> queryList(sql, mapper, params), executor);
    }

    public <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    /**
     * Applies a bundled {@code .sql} resource. Statements are separated by semicolons and
     * {@code --} comment lines are ignored.
     */
    public void applySchema(Plugin owner, String resource) {
        InputStream stream = owner.getResource(resource);
        if (stream == null) {
            plugin.getLogger().warning("Schema resource not found: " + resource);
            return;
        }
        StringBuilder buffer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                buffer.append(trimmed).append(' ');
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read schema " + resource, e);
            return;
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String sql : buffer.toString().split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not apply schema " + resource, e);
        }
    }

    /** {@code INSERT ... ON CONFLICT} differs between the two backends. */
    public String upsert(String table, String columns, String placeholders, String conflictKey, String updates) {
        if (sqlite) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") "
                    + "ON CONFLICT(" + conflictKey + ") DO UPDATE SET " + updates;
        }
        return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ") "
                + "ON DUPLICATE KEY UPDATE " + updates;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        source.close();
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static boolean driverPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet results) throws SQLException;
    }
}

package me.alpha432.corepvp.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Connection pool plus the executor every query runs on.
 *
 * <p>The plugin owns this executor rather than using the Bukkit scheduler,
 * because Bukkit cancels scheduled tasks during shutdown - pending profile
 * saves would be dropped exactly when they matter most. {@link #close()} drains
 * it instead.
 */
public final class Database {

    private final Plugin plugin;
    private final StorageType type;
    private final ConfigurationSection section;

    private HikariDataSource dataSource;
    private ExecutorService executor;

    public Database(Plugin plugin, ConfigurationSection section) {
        this.plugin = plugin;
        this.section = section;
        this.type = StorageType.parse(section.getString("type"), StorageType.SQLITE);
    }

    public StorageType type() {
        return type;
    }

    public void connect() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("CorePvP-" + type.name().toLowerCase(java.util.Locale.ROOT));

        int poolSize;
        switch (type) {
            case MYSQL -> {
                ConfigurationSection my = section.getConfigurationSection("mysql");
                String host = my == null ? "127.0.0.1" : my.getString("host", "127.0.0.1");
                int port = my == null ? 3306 : my.getInt("port", 3306);
                String database = my == null ? "corepvp" : my.getString("database", "corepvp");
                String properties = my == null ? "" : my.getString("properties", "");
                poolSize = Math.max(1, my == null ? 10 : my.getInt("pool-size", 10));

                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                        + (properties == null || properties.isBlank() ? "" : "?" + properties));
                config.setUsername(my == null ? "root" : my.getString("username", "root"));
                config.setPassword(my == null ? "" : my.getString("password", ""));
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }
            default -> {
                ConfigurationSection lite = section.getConfigurationSection("sqlite");
                String fileName = lite == null ? "data.db" : lite.getString("file", "data.db");
                File file = new File(plugin.getDataFolder(), fileName);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    plugin.getLogger().warning("Could not create " + parent);
                }
                // WAL keeps readers from blocking the writer; busy_timeout stops
                // the odd concurrent write from failing outright.
                config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath()
                        + "?journal_mode=WAL&synchronous=NORMAL&busy_timeout=5000");
                config.setDriverClassName("org.sqlite.JDBC");
                // SQLite serialises writes anyway; more than one connection just
                // trades throughput for SQLITE_BUSY.
                poolSize = 1;
            }
        }

        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10));
        config.setLeakDetectionThreshold(TimeUnit.SECONDS.toMillis(30));

        this.dataSource = new HikariDataSource(config);

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "CorePvP-DB-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(poolSize, factory);

        try (Connection connection = dataSource.getConnection()) {
            Schema.apply(connection);
        }
        plugin.getLogger().info("Storage ready (" + type + ").");
    }

    public Connection connection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }

    /** Runs a query off the main thread. The future completes on a DB thread. */
    public <T> CompletableFuture<T> query(SqlFunction<T> function) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try (Connection connection = connection()) {
                future.complete(function.apply(connection));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public CompletableFuture<Void> execute(SqlConsumer consumer) {
        return query(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    /** Runs on the calling thread - only for shutdown, where blocking is fine. */
    public void executeBlocking(SqlConsumer consumer) {
        try (Connection connection = connection()) {
            consumer.accept(connection);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Blocking database call failed", exception);
        }
    }

    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Database executor did not drain in 10s; "
                            + executor.shutdownNow().size() + " task(s) dropped.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}

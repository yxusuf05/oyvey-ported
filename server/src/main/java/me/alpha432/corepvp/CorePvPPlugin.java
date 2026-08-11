package me.alpha432.corepvp;

import me.alpha432.corepvp.command.RootCommand;
import me.alpha432.corepvp.command.impl.ReloadSubCommand;
import me.alpha432.corepvp.command.impl.VersionSubCommand;
import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.menu.MenuListener;
import me.alpha432.corepvp.profile.ProfileListener;
import me.alpha432.corepvp.profile.ProfileManager;
import me.alpha432.corepvp.storage.Database;
import me.alpha432.corepvp.storage.ProfileRepository;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class CorePvPPlugin extends JavaPlugin {

    private static CorePvPPlugin instance;

    private ConfigManager configs;
    private Messages messages;
    private Database database;
    private ProfileRepository profileRepository;
    private ProfileManager profiles;

    public static CorePvPPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Tasks.init(this);

        configs = new ConfigManager(this);
        configs.file("config.yml");
        configs.file("messages.yml");
        messages = new Messages(configs);

        if (!setupStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        profileRepository = new ProfileRepository(database);
        profiles = new ProfileManager(this, database, profileRepository);
        profiles.startAutosave(configs.main().getInt("profiles.autosave-interval-seconds", 300));

        register(new MenuListener());
        register(new ProfileListener(this, profiles, messages,
                configs.main().getBoolean("profiles.kick-on-load-failure", true)));

        registerCommands();

        getLogger().info("CorePvP enabled.");
    }

    @Override
    public void onDisable() {
        // Order matters: nothing may be scheduled during disable, so profiles are
        // flushed synchronously before the pool that would write them is closed.
        if (profiles != null) {
            profiles.shutdown();
        }
        if (database != null) {
            database.close();
        }
        instance = null;
        getLogger().info("CorePvP disabled.");
    }

    private boolean setupStorage() {
        ConfigurationSection storage = configs.main().getConfigurationSection("storage");
        if (storage == null) {
            storage = configs.main().createSection("storage");
        }
        database = new Database(this, storage);
        try {
            database.connect();
            return true;
        } catch (SQLException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not connect to storage - disabling CorePvP", exception);
            return false;
        }
    }

    private void registerCommands() {
        RootCommand root = new RootCommand(messages)
                .register(new ReloadSubCommand(this))
                .register(new VersionSubCommand(this));

        PluginCommand command = getCommand("corepvp");
        if (command == null) {
            getLogger().severe("Command 'corepvp' is missing from plugin.yml");
            return;
        }
        command.setExecutor(root);
        command.setTabCompleter(root);
    }

    public void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    /** Re-reads every YAML file. Safe to call at runtime. */
    public void reloadConfiguration() {
        configs.reloadAll();
        messages.reload();
    }

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public Database database() {
        return database;
    }

    public ProfileManager profiles() {
        return profiles;
    }
}

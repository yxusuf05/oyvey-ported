package me.alpha432.network.core;

import me.alpha432.network.core.board.BoardService;
import me.alpha432.network.core.board.TabService;
import me.alpha432.network.core.command.NetworkCommand;
import me.alpha432.network.core.command.RankCommand;
import me.alpha432.network.core.command.RegionCommand;
import me.alpha432.network.core.economy.EconomyService;
import me.alpha432.network.core.menu.MenuListener;
import me.alpha432.network.core.profile.ProfileService;
import me.alpha432.network.core.rank.RankService;
import me.alpha432.network.core.region.RegionListener;
import me.alpha432.network.core.region.RegionService;
import me.alpha432.network.core.storage.Database;
import me.alpha432.network.core.teleport.TeleportService;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.core.world.VoidGenerator;
import me.alpha432.network.core.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Boots the shared services every other network plugin builds on. */
public final class CorePlugin extends JavaPlugin {

    private Messages messages;
    private Database database;
    private ProfileService profiles;
    private RankService ranks;
    private EconomyService economy;
    private WorldService worlds;
    private RegionService regions;
    private TeleportService teleports;
    private BoardService boards;
    private TabService tabs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Core.bind(this);

        messages = new Messages(this);

        ConfigurationSection storage = getConfig().getConfigurationSection("storage");
        if (storage == null) {
            storage = getConfig().createSection("storage");
        }
        database = new Database(this, storage);
        database.applySchema(this, "schema.sql");

        profiles = new ProfileService(this, database,
                getConfig().getDouble("economy.starting-balance", 100.0D),
                getConfig().getString("ranks.default", "member"));
        economy = new EconomyService(profiles,
                getConfig().getString("economy.symbol", "$"),
                getConfig().getString("economy.format", "#,##0.00"));
        ranks = new RankService(this, profiles, economy);
        worlds = new WorldService(this);
        worlds.loadAll();
        regions = new RegionService(this);
        teleports = new TeleportService(this, messages, getConfig().getInt("teleport.back-history", 5));
        boards = new BoardService(this);
        tabs = new TabService(this, boards, ranks);

        Bukkit.getPluginManager().registerEvents(profiles, this);
        Bukkit.getPluginManager().registerEvents(ranks, this);
        Bukkit.getPluginManager().registerEvents(teleports, this);
        Bukkit.getPluginManager().registerEvents(boards, this);
        Bukkit.getPluginManager().registerEvents(tabs, this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new RegionListener(regions), this);

        // Also published as Bukkit services so third party plugins could use them.
        Bukkit.getServicesManager().register(ProfileService.class, profiles, this, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EconomyService.class, economy, this, ServicePriority.Normal);
        Bukkit.getServicesManager().register(RankService.class, ranks, this, ServicePriority.Normal);

        new RankCommand(this).register();
        new NetworkCommand(this).register();
        new RegionCommand(this).register();

        boards.start(getConfig().getLong("board.update-ticks", 20L));
        tabs.start(getConfig().getLong("tab.update-ticks", 40L));

        long autosave = getConfig().getLong("storage.autosave-minutes", 5L) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            profiles.saveAll();
            profiles.pruneOffline();
        }, autosave, autosave);

        getLogger().info("NetworkCore ready (" + (database.isSqlite() ? "SQLite" : "MySQL") + ").");
    }

    @Override
    public void onDisable() {
        if (boards != null) {
            boards.stop();
        }
        if (tabs != null) {
            tabs.stop();
        }
        if (profiles != null) {
            profiles.saveAll();
        }
        if (regions != null) {
            regions.save();
        }
        if (database != null) {
            database.close();
        }
    }

    /** Lets {@code bukkit.yml} use the void generator for the primary world if wanted. */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return "void".equalsIgnoreCase(id) ? new VoidGenerator() : null;
    }

    /** Re-reads every configuration file without a restart. */
    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        ranks.reload();
        worlds.reload();
        regions.load();
    }

    public Messages messages() {
        return messages;
    }

    public Database database() {
        return database;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public RankService ranks() {
        return ranks;
    }

    public EconomyService economy() {
        return economy;
    }

    public WorldService worlds() {
        return worlds;
    }

    public RegionService regions() {
        return regions;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public BoardService boards() {
        return boards;
    }

    public TabService tabs() {
        return tabs;
    }
}

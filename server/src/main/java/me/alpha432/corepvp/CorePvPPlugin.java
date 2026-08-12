package me.alpha432.corepvp;

import me.alpha432.corepvp.arena.ArenaGenerator;
import me.alpha432.corepvp.arena.ArenaManager;
import me.alpha432.corepvp.arena.BuildProtectionListener;
import me.alpha432.corepvp.arena.rollback.RollbackService;
import me.alpha432.corepvp.board.BoardService;
import me.alpha432.corepvp.combat.CombatListener;
import me.alpha432.corepvp.combat.CombatModeService;
import me.alpha432.corepvp.command.RootCommand;
import me.alpha432.corepvp.command.impl.ArenaSubCommand;
import me.alpha432.corepvp.command.impl.DuelCommand;
import me.alpha432.corepvp.command.impl.InvCommand;
import me.alpha432.corepvp.command.impl.LeaderboardCommand;
import me.alpha432.corepvp.command.impl.LeaveCommand;
import me.alpha432.corepvp.command.impl.StatsCommand;
import me.alpha432.corepvp.command.impl.SpectateCommand;
import me.alpha432.corepvp.command.impl.KitSubCommand;
import me.alpha432.corepvp.command.impl.ReloadSubCommand;
import me.alpha432.corepvp.command.impl.SetSpawnSubCommand;
import me.alpha432.corepvp.command.impl.SpawnSubCommand;
import me.alpha432.corepvp.command.impl.VersionSubCommand;
import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.duel.DuelService;
import me.alpha432.corepvp.elo.EloService;
import me.alpha432.corepvp.elo.LeaderboardService;
import me.alpha432.corepvp.kit.KitApplier;
import me.alpha432.corepvp.kit.KitManager;
import me.alpha432.corepvp.kit.layout.KitEditorMenu;
import me.alpha432.corepvp.kit.layout.KitLayoutService;
import me.alpha432.corepvp.lobby.HubItem;
import me.alpha432.corepvp.lobby.LobbyBoardProvider;
import me.alpha432.corepvp.lobby.LobbyListener;
import me.alpha432.corepvp.lobby.LobbyService;
import me.alpha432.corepvp.match.MatchBoardProvider;
import me.alpha432.corepvp.match.MatchListener;
import me.alpha432.corepvp.match.MatchManager;
import me.alpha432.corepvp.match.snapshot.SnapshotService;
import me.alpha432.corepvp.menu.MenuListener;
import me.alpha432.corepvp.queue.QueueManager;
import me.alpha432.corepvp.queue.QueueMenu;
import me.alpha432.corepvp.profile.ProfileListener;
import me.alpha432.corepvp.profile.ProfileManager;
import me.alpha432.corepvp.rank.ChatListener;
import me.alpha432.corepvp.rank.NameTagService;
import me.alpha432.corepvp.rank.RankManager;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.state.PlayerStateService;
import me.alpha432.corepvp.storage.Database;
import me.alpha432.corepvp.storage.ProfileRepository;
import me.alpha432.corepvp.util.Tasks;
import me.alpha432.corepvp.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
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

    private WorldService worlds;
    private PlayerStateService states;
    private RankManager ranks;
    private BoardService boards;
    private NameTagService nameTags;
    private LobbyService lobby;
    private LobbyBoardProvider lobbyBoard;

    private CombatModeService combat;
    private KitManager kits;
    private KitApplier kitApplier;
    private RollbackService rollback;
    private ArenaManager arenas;
    private ArenaGenerator arenaGenerator;
    private BuildProtectionListener buildProtection;
    private SnapshotService snapshots;
    private MatchManager matches;
    private DuelService duels;
    private QueueManager queues;
    private EloService elo;
    private LeaderboardService leaderboards;
    private KitLayoutService layouts;

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
        configs.file("ranks.yml");
        messages = new Messages(configs);

        if (!setupStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        profileRepository = new ProfileRepository(database);
        profiles = new ProfileManager(this, database, profileRepository);
        profiles.startAutosave(configs.main().getInt("profiles.autosave-interval-seconds", 300));

        // Worlds first: the lobby spawn is stored as a world name plus
        // coordinates and cannot be resolved before its world exists.
        worlds = new WorldService(this);
        worlds.load(section("worlds"));

        states = new PlayerStateService();
        ranks = new RankManager(configs);
        boards = new BoardService(states, profiles);
        nameTags = new NameTagService(ranks, boards);
        lobby = new LobbyService(this, configs, messages, states, worlds, nameTags);

        combat = new CombatModeService(this);
        kitApplier = new KitApplier(combat);
        layouts = new KitLayoutService(this, database);
        kitApplier.layouts(layouts);
        profiles.layouts(layouts);
        kits = new KitManager(this, configs);
        kits.load();

        rollback = new RollbackService(this,
                configs.main().getInt("arenas.rollback-blocks-per-tick", 2000));
        arenas = new ArenaManager(this, configs, rollback);
        arenas.load();
        arenaGenerator = new ArenaGenerator(this, arenas,
                configs.main().getInt("arenas.blocks-per-tick", 4000));
        buildProtection = new BuildProtectionListener(arenas);

        snapshots = new SnapshotService(configs.main().getInt("match.snapshot-cache-size", 200));
        matches = new MatchManager(this, snapshots);
        duels = new DuelService(this);

        // Building is decided by the kit of the match the player is in.
        buildProtection.policy(player -> {
            var match = matches.matchOf(player);
            return match != null && match.contains(player.getUniqueId())
                    && match.kit().flags().build();
        });

        queues = new QueueManager(this);
        elo = new EloService(this);
        leaderboards = new LeaderboardService(this, database,
                configs.main().getInt("leaderboard.entries", 30));
        matches.onEnd(elo::apply);

        // Hub items only appear once something has claimed their slot.
        lobby.setAction(HubItem.UNRANKED_QUEUE, player -> new QueueMenu(this, false).open(player));
        lobby.setAction(HubItem.RANKED_QUEUE, player -> new QueueMenu(this, true).open(player));
        lobby.setAction(HubItem.KIT_EDITOR, player -> new KitEditorMenu(this).open(player));
        lobby.setAction(HubItem.LEADERBOARD, player -> {
            var kits = kits().enabled();
            if (!kits.isEmpty()) {
                leaderboards.refresh(kits.get(0).id());
                new me.alpha432.corepvp.elo.LeaderboardMenu(this, kits.get(0).id()).open(player);
            }
        });

        lobbyBoard = new LobbyBoardProvider(messages, configs, ranks);
        lobbyBoard.inFights(matches::fightingCount);
        lobbyBoard.inQueue(queues::total);
        boards.register(PlayerState.LOBBY, lobbyBoard);
        boards.register(PlayerState.QUEUE, lobbyBoard);

        MatchBoardProvider matchBoard = new MatchBoardProvider(messages, matches);
        boards.register(PlayerState.MATCH_STARTING, matchBoard);
        boards.register(PlayerState.MATCH_FIGHTING, matchBoard);
        boards.register(PlayerState.MATCH_DEAD, matchBoard);
        boards.register(PlayerState.SPECTATING, matchBoard);

        register(new MenuListener());
        register(buildProtection);
        register(new CombatListener(combat));
        register(new MatchListener(this, matches));
        matches.start();
        queues.start();
        register(queues.quitListener());
        leaderboards.start(kits.ids(), configs.main().getLong("leaderboard.refresh-seconds", 60L));
        register(new ProfileListener(this, profiles, messages,
                configs.main().getBoolean("profiles.kick-on-load-failure", true)));
        LobbyListener lobbyListener = new LobbyListener(lobby, states, boards, nameTags, messages);
        lobbyListener.onQueueLeave(player -> queues.leave(player));
        register(lobbyListener);
        register(new ChatListener(ranks, messages, configs));

        if (configs.main().getBoolean("scoreboard.enabled", true)) {
            boards.start(Math.max(1, configs.main().getInt("scoreboard.update-ticks", 4)));
        }

        registerCommands();
        adoptOnlinePlayers();

        getLogger().info("CorePvP enabled.");
    }

    @Override
    public void onDisable() {
        // Order matters: nothing may be scheduled during disable, so profiles are
        // flushed synchronously before the pool that would write them is closed.
        if (boards != null) {
            boards.stop();
        }
        if (queues != null) {
            queues.stop();
        }
        if (leaderboards != null) {
            leaderboards.stop();
        }
        if (matches != null) {
            matches.stop();
            matches.endAllBlocking();
        }
        // Arenas are restored on the calling thread: the scheduler is already
        // gone at this point, so a task-based rollback would never run and the
        // arena would keep the last match's blocks forever.
        if (arenas != null) {
            arenas.restoreAllBlocking();
        }
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
        database = new Database(this, section("storage"));
        try {
            database.connect();
            return true;
        } catch (SQLException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not connect to storage - disabling CorePvP", exception);
            return false;
        }
    }

    private ConfigurationSection section(String path) {
        ConfigurationSection section = configs.main().getConfigurationSection(path);
        return section == null ? configs.main().createSection(path) : section;
    }

    /** Picks up players who were already connected, e.g. after /reload. */
    private void adoptOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (profiles.get(player) == null) {
                database.query(connection ->
                                profileRepository.load(connection, player.getUniqueId(), player.getName()))
                        .thenAccept(profile -> Tasks.sync(() -> {
                            boards.create(player);
                            nameTags.populate(player);
                            nameTags.update(player);
                            lobby.sendToLobby(player);
                        }));
            }
        }
    }

    private void registerCommands() {
        RootCommand root = new RootCommand(messages)
                .register(new SpawnSubCommand(lobby, messages))
                .register(new SetSpawnSubCommand(lobby, messages))
                .register(new ArenaSubCommand(arenas, arenaGenerator, worlds, messages))
                .register(new KitSubCommand(kits, kitApplier, messages))
                .register(new ReloadSubCommand(this))
                .register(new VersionSubCommand(this));

        PluginCommand command = getCommand("corepvp");
        if (command == null) {
            getLogger().severe("Command 'corepvp' is missing from plugin.yml");
            return;
        }
        command.setExecutor(root);
        command.setTabCompleter(root);

        bind("duel", new DuelCommand(this, duels));
        bind("spectate", new SpectateCommand(this));
        bind("inv", new InvCommand(this));
        bind("leave", new LeaveCommand(this));
        bind("stats", new StatsCommand(this));
        bind("leaderboard", new LeaderboardCommand(this));
    }

    private void bind(String name, me.alpha432.corepvp.command.SimpleCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    /** Re-reads every YAML file. Safe to call at runtime. */
    public void reloadConfiguration() {
        configs.reloadAll();
        messages.reload();
        ranks.reload();
        lobby.reload();
        kits.load();
        matches.reload();
        queues.reload();
        nameTags.updateAll();
        // Arenas are deliberately not reloaded: a running match holds a live
        // Arena object, and swapping it out underneath would strand its
        // rollback journal.
    }

    public CombatModeService combat() {
        return combat;
    }

    public KitManager kits() {
        return kits;
    }

    public KitApplier kitApplier() {
        return kitApplier;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public ArenaGenerator arenaGenerator() {
        return arenaGenerator;
    }

    public BuildProtectionListener buildProtection() {
        return buildProtection;
    }

    public SnapshotService snapshots() {
        return snapshots;
    }

    public MatchManager matches() {
        return matches;
    }

    public DuelService duels() {
        return duels;
    }

    public QueueManager queues() {
        return queues;
    }

    public EloService elo() {
        return elo;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public KitLayoutService layouts() {
        return layouts;
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

    public WorldService worlds() {
        return worlds;
    }

    public PlayerStateService states() {
        return states;
    }

    public RankManager ranks() {
        return ranks;
    }

    public BoardService boards() {
        return boards;
    }

    public NameTagService nameTags() {
        return nameTags;
    }

    public LobbyService lobby() {
        return lobby;
    }

    public LobbyBoardProvider lobbyBoard() {
        return lobbyBoard;
    }
}

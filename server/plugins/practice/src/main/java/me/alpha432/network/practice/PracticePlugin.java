package me.alpha432.network.practice;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.practice.arena.ArenaService;
import me.alpha432.network.practice.board.PracticeBoardProvider;
import me.alpha432.network.practice.command.ArenaCommand;
import me.alpha432.network.practice.command.DuelCommands;
import me.alpha432.network.practice.command.PracticeCommands;
import me.alpha432.network.practice.ffa.FfaService;
import me.alpha432.network.practice.kit.KitService;
import me.alpha432.network.practice.kit.PracticeKit;
import me.alpha432.network.practice.listener.PracticeListener;
import me.alpha432.network.practice.match.MatchService;
import me.alpha432.network.practice.queue.QueueService;
import me.alpha432.network.practice.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Duels, ranked queues with Elo, free-for-all arenas, kit editor and leaderboards. */
public final class PracticePlugin extends JavaPlugin {

    private Messages messages;
    private HubService hub;
    private KitService kits;
    private ArenaService arenas;
    private StatsService stats;
    private MatchService matches;
    private QueueService queue;
    private FfaService ffa;
    private PracticeBoardProvider board;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        Core.database().applySchema(this, "schema.sql");

        hub = new HubService(this);
        kits = new KitService(this, Core.database());
        arenas = new ArenaService(this);
        stats = new StatsService(this, Core.database(), getConfig().getInt("ranked.starting-elo", 1000));
        matches = new MatchService(this);
        queue = new QueueService(this);
        ffa = new FfaService(this);

        Bukkit.getPluginManager().registerEvents(new PracticeListener(this), this);
        PracticeCommands.register(this);
        DuelCommands.register(this);
        new ArenaCommand(this).register();

        board = new PracticeBoardProvider(this);
        Core.boards().register(board);
        queue.start();

        long prune = getConfig().getLong("queue.prune-interval-ticks", 200L);
        Bukkit.getScheduler().runTaskTimer(this, queue::prune, prune, prune);

        if (Bukkit.getWorld(hub.worldName()) == null) {
            getLogger().warning("Practice world '" + hub.worldName() + "' does not exist. "
                    + "Add it to NetworkCore's worlds.yml or change 'world' in this plugin's config.yml.");
        }
        getLogger().info("NetworkPractice enabled with " + kits.all().size() + " kits and "
                + arenas.all().size() + " arenas.");
    }

    @Override
    public void onDisable() {
        if (queue != null) {
            queue.stop();
        }
        if (matches != null) {
            matches.endAll();
        }
        if (ffa != null) {
            ffa.leaveAll();
        }
        if (arenas != null) {
            arenas.releaseAll();
        }
        if (stats != null) {
            stats.saveAll();
        }
        if (board != null && Core.isReady()) {
            Core.boards().unregister(board);
        }
    }

    /** Puts a player into a queue after checking that a fitting arena exists at all. */
    public void joinQueue(Player player, PracticeKit kit, boolean ranked, String arena) {
        if (matches.isInMatch(player)) {
            messages.send(player, "queue.in-match");
            return;
        }
        if (ffa.isPlaying(player)) {
            messages.send(player, "queue.in-ffa");
            return;
        }
        if (arenas.supporting(kit.id()).isEmpty()) {
            messages.send(player, "queue.no-arena", "<kit>", kit.displayName());
            return;
        }
        if (!queue.join(player, kit, ranked, arena)) {
            messages.send(player, "queue.already-queued");
            return;
        }
        messages.send(player, "queue.joined",
                "<kit>", kit.displayName(),
                "<mode>", messages.raw(ranked ? "board.mode-ranked" : "board.mode-unranked"),
                "<map>", arena == null ? messages.raw("board.map-random") : arena);
    }

    public void joinFfa(Player player, FfaService.FfaArena arena) {
        if (matches.isInMatch(player)) {
            messages.send(player, "queue.in-match");
            return;
        }
        queue.leave(player);
        ffa.join(player, arena);
    }

    /** Sends the player back to the lobby world. */
    public void sendToLobby(Player player) {
        World lobby = Bukkit.getWorld(getConfig().getString("lobby-world", "lobby"));
        if (lobby == null) {
            messages.send(player, "error.no-lobby");
            return;
        }
        player.getInventory().clear();
        Core.teleports().teleport(player, lobby.getSpawnLocation(), false);
    }

    /** The display name of an Elo tier, e.g. {@code gold} to "Gold". */
    public String tierName(String tier) {
        return messages.raw("tier." + tier);
    }

    public String defaultKitId() {
        return getConfig().getString("default-kit", "nodebuff");
    }

    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        kits.reload();
        arenas.load();
        ffa.load();
        hub.load();
    }

    public Messages messages() {
        return messages;
    }

    public HubService hub() {
        return hub;
    }

    public KitService kits() {
        return kits;
    }

    public ArenaService arenas() {
        return arenas;
    }

    public StatsService stats() {
        return stats;
    }

    public MatchService matches() {
        return matches;
    }

    public QueueService queue() {
        return queue;
    }

    public FfaService ffa() {
        return ffa;
    }
}

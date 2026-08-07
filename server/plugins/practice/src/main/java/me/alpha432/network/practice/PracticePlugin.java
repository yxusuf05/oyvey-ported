package me.alpha432.network.practice;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.practice.arena.ArenaService;
import me.alpha432.network.practice.board.PracticeBoardProvider;
import me.alpha432.network.practice.bot.BotBehavior;
import me.alpha432.network.practice.bot.BotListener;
import me.alpha432.network.practice.bot.BotService;
import me.alpha432.network.practice.bot.BotSettings;
import me.alpha432.network.practice.bot.PracticeBot;
import me.alpha432.network.practice.command.ArenaCommand;
import me.alpha432.network.practice.command.BotCommand;
import me.alpha432.network.practice.command.DuelCommands;
import me.alpha432.network.practice.command.PracticeCommands;
import me.alpha432.network.practice.ffa.FfaService;
import me.alpha432.network.practice.kit.KitService;
import me.alpha432.network.practice.kit.PracticeKit;
import me.alpha432.network.practice.listener.ArenaResetListener;
import me.alpha432.network.practice.listener.PracticeListener;
import me.alpha432.network.practice.match.MatchService;
import me.alpha432.network.practice.queue.QueueService;
import me.alpha432.network.practice.stats.StatsService;
import me.alpha432.network.practice.world.PvpWorldService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    private BotService bots;
    private PvpWorldService pvpWorld;
    /** Remembered per player so /bot spawn keeps the last settings. */
    private final Map<UUID, BotSettings> botSettings = new HashMap<>();

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
        bots = new BotService(this);
        pvpWorld = new PvpWorldService(this);
        pvpWorld.load();
        Core.randomTeleports().register(pvpWorld);

        Bukkit.getPluginManager().registerEvents(new PracticeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BotListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ArenaResetListener(this), this);
        PracticeCommands.register(this);
        DuelCommands.register(this);
        new ArenaCommand(this).register();
        new BotCommand(this).register();

        board = new PracticeBoardProvider(this);
        Core.boards().register(board);
        queue.start();
        bots.start();

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
        if (bots != null) {
            bots.stop();
            bots.removeAll();
        }
        if (pvpWorld != null && Core.isReady()) {
            Core.randomTeleports().unregister(pvpWorld);
        }
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
        pvpWorld.load();
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

    public BotService bots() {
        return bots;
    }

    public PvpWorldService pvpWorld() {
        return pvpWorld;
    }

    /** The remembered bot settings of a player, created with the defaults on first use. */
    public BotSettings botSettingsOf(Player player) {
        return botSettings.computeIfAbsent(player.getUniqueId(),
                id -> new BotSettings(defaultKitId()));
    }

    /** Gives the player the same loadout the bot wears, so the training is a fair fight. */
    public void equipForBotFight(Player player, me.alpha432.network.practice.kit.PracticeKit kit) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(attribute == null ? 20.0D : attribute.getValue());
        player.setMaximumNoDamageTicks(kit.damageTicks());
        player.getInventory().setStorageContents(kits.contentsFor(player, kit));
        if (kit.helmet() != null) {
            player.getInventory().setHelmet(kit.helmet().clone());
        }
        if (kit.chestplate() != null) {
            player.getInventory().setChestplate(kit.chestplate().clone());
        }
        if (kit.leggings() != null) {
            player.getInventory().setLeggings(kit.leggings().clone());
        }
        if (kit.boots() != null) {
            player.getInventory().setBoots(kit.boots().clone());
        }
        kit.effects().forEach(player::addPotionEffect);
    }

    /** Live combo counter on the action bar while training. */
    public void showBotStats(Player player, PracticeBot bot) {
        messages.actionBar(player, "bot.actionbar",
                "<combo>", String.valueOf(bot.stats().combo()),
                "<best>", String.valueOf(bot.stats().bestCombo()),
                "<hits>", String.valueOf(bot.stats().hits()),
                "<accuracy>", String.format("%.0f", bot.stats().accuracy()),
                "<health>", String.format("%.1f", bot.health()));
    }

    /** Puts a defeated or reset bot back on its feet a moment later. */
    public void respawnBot(PracticeBot bot) {
        kits.get(bot.settings().kitId()).ifPresent(kit ->
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (bots.of(bot.owner()) == bot) {
                        bot.reset(kit);
                    } else {
                        bots.spawn(bot.owner(), bot.settings());
                    }
                }, getConfig().getLong("bot.respawn-delay-ticks", 40L)));
    }

    /** The readable name of a bot behaviour, taken from messages.yml. */
    public String behaviorName(BotBehavior behavior) {
        return messages.raw("bot.mode-" + behavior.id());
    }
}

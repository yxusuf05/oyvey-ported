package me.alpha432.corepvp.queue;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.match.MatchType;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The waiting rooms, and the loop that empties them.
 *
 * <p>Matchmaking runs on the main thread once a second. The pairing itself is
 * in {@link Matchmaker}, which has no Bukkit types and is unit tested; this
 * class only handles the parts that need a live server.
 */
public final class QueueManager {

    /** One waiting party. A solo player is a party of one. */
    public record Entry(UUID leaderId, List<UUID> members, int elo, long since) {
    }

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Matchmaker matchmaker = new Matchmaker();

    private final Map<QueueKey, List<Entry>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, QueueKey> byPlayer = new ConcurrentHashMap<>();

    private BukkitTask task;
    private EloRangePolicy policy = EloRangePolicy.defaults();

    public QueueManager(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        reload();
    }

    public void reload() {
        policy = new EloRangePolicy(
                plugin.configs().main().getInt("queue.elo-range-base", 50),
                plugin.configs().main().getInt("queue.elo-range-expansion-per-second", 25),
                plugin.configs().main().getInt("queue.elo-range-max", 5000));
    }

    public void start() {
        stop();
        task = Tasks.timer(this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------
    //  Joining and leaving
    // ------------------------------------------------------------------

    public boolean join(Player player, Kit kit, boolean ranked) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            messages.send(player, "queue.already");
            return false;
        }
        if (plugin.matches().matchOf(player) != null) {
            messages.send(player, "queue.in-match");
            return false;
        }
        if (ranked && !kit.flags().rankedEnabled()) {
            messages.send(player, "queue.not-ranked", Messages.of("kit", kit.displayName()));
            return false;
        }

        Profile profile = plugin.profiles().require(player);
        if (ranked) {
            int required = plugin.configs().main().getInt("queue.ranked-min-games", 0);
            int played = profile.kit(kit.id()).played();
            if (played < required && !player.hasPermission("corepvp.ranked.bypass")) {
                messages.send(player, "queue.ranked-locked",
                        Messages.of("kit", kit.displayName()),
                        Messages.of("needed", required - played));
                return false;
            }
        }

        QueueKey key = QueueKey.solo(kit.id(), ranked);
        Entry entry = new Entry(player.getUniqueId(), List.of(player.getUniqueId()),
                profile.kit(kit.id()).elo(), System.currentTimeMillis());

        queues.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        byPlayer.put(player.getUniqueId(), key);
        plugin.states().set(player, PlayerState.QUEUE);
        plugin.lobby().giveQueueItems(player);

        messages.send(player, ranked ? "queue.joined-ranked" : "queue.joined",
                Messages.of("kit", kit.displayName()),
                Messages.of("elo", entry.elo()));
        return true;
    }

    public boolean leave(Player player) {
        QueueKey key = byPlayer.remove(player.getUniqueId());
        if (key == null) {
            return false;
        }
        List<Entry> queue = queues.get(key);
        if (queue != null) {
            queue.removeIf(entry -> entry.leaderId().equals(player.getUniqueId()));
        }
        if (player.isOnline()) {
            plugin.lobby().sendToLobby(player);
            messages.send(player, "queue.left");
        }
        return true;
    }

    /** Silent removal, for players who are being put into a match or quit. */
    private void remove(UUID uuid) {
        QueueKey key = byPlayer.remove(uuid);
        if (key == null) {
            return;
        }
        List<Entry> queue = queues.get(key);
        if (queue != null) {
            queue.removeIf(entry -> entry.leaderId().equals(uuid));
        }
    }

    public void handleQuit(Player player) {
        remove(player.getUniqueId());
    }

    /** Small listener so the queue cleans itself up without the lobby knowing. */
    public org.bukkit.event.Listener quitListener() {
        return new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                handleQuit(event.getPlayer());
            }
        };
    }

    public boolean inQueue(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    public QueueKey queueOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public int size(QueueKey key) {
        List<Entry> queue = queues.get(key);
        return queue == null ? 0 : queue.size();
    }

    public int total() {
        return byPlayer.size();
    }

    public Map<QueueKey, Integer> sizes() {
        Map<QueueKey, Integer> sizes = new LinkedHashMap<>();
        queues.forEach((key, entries) -> sizes.put(key, entries.size()));
        return sizes;
    }

    // ------------------------------------------------------------------
    //  The loop
    // ------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<QueueKey, List<Entry>> queue : queues.entrySet()) {
            QueueKey key = queue.getKey();
            List<Entry> entries = queue.getValue();
            if (entries.size() < 2) {
                continue;
            }

            Kit kit = plugin.kits().byId(key.kitId());
            if (kit == null) {
                continue;
            }

            // Drop anyone who logged out or ended up in a match some other way.
            entries.removeIf(entry -> {
                Player player = plugin.getServer().getPlayer(entry.leaderId());
                return player == null || !player.isOnline() || plugin.matches().matchOf(player) != null;
            });

            List<Matchmaker.Ticket> tickets = new ArrayList<>();
            for (Entry entry : entries) {
                tickets.add(new Matchmaker.Ticket(entry.leaderId(), entry.elo(), entry.since(), entry.members()));
            }

            List<Matchmaker.Pairing> pairings = key.ranked()
                    ? matchmaker.pairRanked(tickets, now, policy)
                    : matchmaker.pairUnranked(tickets);

            for (Matchmaker.Pairing pairing : pairings) {
                startMatch(key, kit, pairing);
            }
        }
    }

    private void startMatch(QueueKey key, Kit kit, Matchmaker.Pairing pairing) {
        List<Player> sideA = online(pairing.a().members());
        List<Player> sideB = online(pairing.b().members());
        if (sideA.isEmpty() || sideB.isEmpty()) {
            return;
        }

        // The arena is taken first. If none is free the pairing is abandoned
        // and both entries stay queued - consuming them here would silently
        // drop players out of the queue on a busy server.
        Arena arena = plugin.arenas().acquire(kit);
        if (arena == null) {
            return;
        }

        sideA.forEach(player -> remove(player.getUniqueId()));
        sideB.forEach(player -> remove(player.getUniqueId()));

        plugin.matches().create(kit, key.ranked() ? MatchType.RANKED : MatchType.UNRANKED,
                arena, List.of(sideA, sideB));
    }

    private List<Player> online(List<UUID> members) {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }
}

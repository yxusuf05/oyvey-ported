package me.alpha432.network.practice.queue;

import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.arena.Arena;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Unranked and ranked queues. Ranked pairs players whose ratings are close; the window grows
 * the longer somebody waits, so nobody is stuck forever.
 */
public final class QueueService {

    /**
     * One player waiting.
     *
     * @param arena a specific map, or {@code null} for a random one
     */
    public record Entry(UUID player, String kit, boolean ranked, String arena, int elo, long since) {
        public long waitedSeconds() {
            return (System.currentTimeMillis() - since) / 1000L;
        }
    }

    private final PracticePlugin plugin;
    private final List<Entry> waiting = new ArrayList<>();
    private final Map<UUID, Entry> byPlayer = new HashMap<>();
    private BukkitTask task;

    public QueueService(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long period = plugin.getConfig().getLong("queue.match-interval-ticks", 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isQueued(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    public Entry entryOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public int size() {
        return waiting.size();
    }

    /** How many players wait for one kit, used in the queue menu. */
    public int size(String kit, boolean ranked) {
        int count = 0;
        for (Entry entry : waiting) {
            if (entry.kit().equals(kit) && entry.ranked() == ranked) {
                count++;
            }
        }
        return count;
    }

    /** @return false when the player is already queued. */
    public boolean join(Player player, PracticeKit kit, boolean ranked, String arena) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            return false;
        }
        int elo = ranked ? plugin.stats().get(player.getUniqueId(), kit.id()).elo() : 0;
        Entry entry = new Entry(player.getUniqueId(), kit.id(), ranked, arena, elo,
                System.currentTimeMillis());
        waiting.add(entry);
        byPlayer.put(player.getUniqueId(), entry);
        return true;
    }

    public boolean leave(Player player) {
        Entry entry = byPlayer.remove(player.getUniqueId());
        if (entry == null) {
            return false;
        }
        waiting.remove(entry);
        return true;
    }

    /** Tries to pair everyone who is waiting; runs on the main thread. */
    private void tick() {
        for (int i = 0; i < waiting.size(); i++) {
            Entry first = waiting.get(i);
            Player firstPlayer = Bukkit.getPlayer(first.player());
            if (firstPlayer == null) {
                remove(first);
                i--;
                continue;
            }
            Entry partner = findPartner(first, i + 1);
            if (partner == null) {
                continue;
            }
            Player secondPlayer = Bukkit.getPlayer(partner.player());
            if (secondPlayer == null) {
                remove(partner);
                i--;
                continue;
            }
            if (!tryStart(first, partner, firstPlayer, secondPlayer)) {
                // No free arena right now; try again on the next tick.
                continue;
            }
            i--;
        }
    }

    private Entry findPartner(Entry first, int from) {
        for (int j = from; j < waiting.size(); j++) {
            Entry candidate = waiting.get(j);
            if (!candidate.kit().equals(first.kit()) || candidate.ranked() != first.ranked()) {
                continue;
            }
            if (candidate.player().equals(first.player())) {
                continue;
            }
            if (first.arena() != null && candidate.arena() != null
                    && !first.arena().equals(candidate.arena())) {
                continue;
            }
            if (first.ranked() && !withinEloWindow(first, candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** The allowed rating gap starts at a base value and widens with the longer wait. */
    private boolean withinEloWindow(Entry a, Entry b) {
        int base = plugin.getConfig().getInt("ranked.elo-window", 200);
        int perSecond = plugin.getConfig().getInt("ranked.elo-window-growth-per-second", 20);
        long waited = Math.max(a.waitedSeconds(), b.waitedSeconds());
        int window = base + (int) (waited * perSecond);
        return Math.abs(a.elo() - b.elo()) <= window;
    }

    private boolean tryStart(Entry first, Entry second, Player firstPlayer, Player secondPlayer) {
        Optional<PracticeKit> kit = plugin.kits().get(first.kit());
        if (kit.isEmpty()) {
            remove(first);
            remove(second);
            return true;
        }
        String preferred = first.arena() != null ? first.arena() : second.arena();
        Optional<Arena> arena = plugin.arenas().reserve(first.kit(), preferred);
        if (arena.isEmpty()) {
            return false;
        }
        remove(first);
        remove(second);
        plugin.matches().start(firstPlayer, secondPlayer, kit.get(), arena.get(), first.ranked());
        return true;
    }

    private void remove(Entry entry) {
        waiting.remove(entry);
        byPlayer.remove(entry.player());
    }

    /** Drops queue entries of players that went offline. */
    public void prune() {
        Iterator<Entry> iterator = waiting.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (Bukkit.getPlayer(entry.player()) == null) {
                iterator.remove();
                byPlayer.remove(entry.player());
            }
        }
    }
}

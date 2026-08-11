package me.alpha432.corepvp.match.snapshot;

import me.alpha432.corepvp.match.Match;
import me.alpha432.corepvp.match.MatchTeam;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps recent match inventories so players can look at how a fight ended.
 *
 * <p>Held in memory with a bounded LRU rather than written to the database:
 * nobody opens a post-match inventory hours later, and persisting them would
 * add megabytes an hour for no benefit.
 */
public final class SnapshotService {

    private final int capacity;
    private final AtomicLong counter = new AtomicLong();
    private final Map<String, MatchSnapshot> snapshots;

    public SnapshotService(int capacity) {
        this.capacity = Math.max(16, capacity);
        this.snapshots = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, MatchSnapshot> eldest) {
                return size() > SnapshotService.this.capacity;
            }
        };
    }

    /** Short base-36 id, e.g. "3f" - short enough to type into a command. */
    public String nextId() {
        return Long.toString(counter.incrementAndGet(), 36);
    }

    public synchronized MatchSnapshot capture(Match match) {
        List<PlayerSnapshot> players = new ArrayList<>();
        for (MatchTeam team : match.teams()) {
            boolean winner = match.winner() == team;
            for (java.util.UUID uuid : team.members()) {
                // Players who died already had their state recorded at the
                // moment of death, before the inventory was cleared.
                PlayerSnapshot recorded = match.recordedSnapshot(uuid);
                if (recorded != null) {
                    players.add(recorded.withWinner(winner));
                    continue;
                }
                Player player = org.bukkit.Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    players.add(PlayerSnapshot.capture(player, match.stats(uuid), winner));
                }
            }
        }
        MatchSnapshot snapshot = new MatchSnapshot(match.shortId(), match.kit().displayName(),
                System.currentTimeMillis(), players);
        snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    public synchronized MatchSnapshot get(String shortId) {
        return shortId == null ? null : snapshots.get(shortId.toLowerCase(Locale.ROOT));
    }

    public synchronized int size() {
        return snapshots.size();
    }

    public synchronized void clear() {
        snapshots.clear();
    }
}

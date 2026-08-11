package me.alpha432.corepvp.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Decides who plays whom.
 *
 * <p>Deliberately free of Bukkit types so the pairing rules can be tested
 * directly, and run on the main thread: sorting a few hundred entries costs
 * microseconds, while matchmaking off-thread is the classic way to end up with
 * one player in two matches.
 */
public final class Matchmaker {

    /** One party (or lone player) waiting in a queue. */
    public record Ticket(UUID id, int elo, long enqueuedAt, List<UUID> members) {

        public Ticket(UUID id, int elo, long enqueuedAt) {
            this(id, elo, enqueuedAt, List.of(id));
        }
    }

    public record Pairing(Ticket a, Ticket b) {
    }

    /** First in, first out. Rating is ignored. */
    public List<Pairing> pairUnranked(List<Ticket> tickets) {
        List<Ticket> queue = new ArrayList<>(tickets);
        queue.sort(Comparator.comparingLong(Ticket::enqueuedAt));

        List<Pairing> pairings = new ArrayList<>();
        for (int i = 0; i + 1 < queue.size(); i += 2) {
            pairings.add(new Pairing(queue.get(i), queue.get(i + 1)));
        }
        return pairings;
    }

    /**
     * Pairs by rating, widening the acceptable gap as people wait.
     *
     * <p>Longest-waiting tickets are served first so nobody starves, and a pair
     * is allowed when <em>either</em> side's widened range covers the gap. That
     * second rule matters: without it, a player who has waited two minutes
     * still cannot be matched with someone who just joined, and on a quiet
     * server the queue deadlocks with two people in it.
     */
    public List<Pairing> pairRanked(List<Ticket> tickets, long now, EloRangePolicy policy) {
        List<Ticket> byElo = new ArrayList<>(tickets);
        byElo.sort(Comparator.comparingInt(Ticket::elo));

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < byElo.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingLong(index -> byElo.get(index).enqueuedAt()));

        boolean[] taken = new boolean[byElo.size()];
        List<Pairing> pairings = new ArrayList<>();

        for (int index : order) {
            if (taken[index]) {
                continue;
            }
            Ticket a = byElo.get(index);
            int rangeA = policy.range(now - a.enqueuedAt());

            int best = -1;
            int bestGap = Integer.MAX_VALUE;
            // Walk outwards from this rating: the first distance that yields a
            // legal partner also yields the closest one.
            for (int distance = 1; distance < byElo.size() && best == -1; distance++) {
                for (int candidate : new int[]{index - distance, index + distance}) {
                    if (candidate < 0 || candidate >= byElo.size() || taken[candidate]) {
                        continue;
                    }
                    Ticket b = byElo.get(candidate);
                    int gap = Math.abs(a.elo() - b.elo());
                    int allowed = Math.max(rangeA, policy.range(now - b.enqueuedAt()));
                    if (gap <= allowed && gap < bestGap) {
                        best = candidate;
                        bestGap = gap;
                    }
                }
            }

            if (best != -1) {
                taken[index] = true;
                taken[best] = true;
                pairings.add(new Pairing(a, byElo.get(best)));
            }
        }
        return pairings;
    }
}

package me.alpha432.corepvp.arena.rollback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers what an arena looked like before a match touched it.
 *
 * <p>The one rule is <b>first write wins</b>: only the state a block had before
 * the match is stored, no matter how often it changes afterwards. That makes
 * restoring order-independent and idempotent - it can run twice, or resume
 * after a crash, and the arena still ends up exactly as it started. It is also
 * what lets this class be tested without a server: it holds no Bukkit types.
 *
 * @param <S> whatever the caller needs to restore one block
 */
public final class RollbackJournal<S> {

    private final Map<Long, S> original = new LinkedHashMap<>();

    /**
     * Records the pre-match state of a block.
     *
     * @return true if this was the first change to that position
     */
    public boolean record(long key, S before) {
        return original.putIfAbsent(key, before) == null;
    }

    public boolean contains(long key) {
        return original.containsKey(key);
    }

    public S get(long key) {
        return original.get(key);
    }

    public int size() {
        return original.size();
    }

    public boolean isEmpty() {
        return original.isEmpty();
    }

    /**
     * Positions in the order they should be restored: top down, so blocks that
     * need support are placed after the block below them exists again.
     */
    public List<Map.Entry<Long, S>> restoreOrder() {
        List<Map.Entry<Long, S>> entries = new ArrayList<>(original.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<Long, S> entry) -> BlockKey.y(entry.getKey())).reversed());
        return entries;
    }

    public void clear() {
        original.clear();
    }
}

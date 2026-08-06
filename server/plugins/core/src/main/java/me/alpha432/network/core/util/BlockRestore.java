package me.alpha432.network.core.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Records the state a block had before a match touched it so the arena can be rolled back
 * afterwards. Only the first change per block is kept, which is what a rollback needs.
 */
public final class BlockRestore {

    private final Deque<BlockState> snapshots = new ArrayDeque<>();
    private final Set<Long> seen = new HashSet<>();

    public void record(Block block) {
        long key = key(block.getX(), block.getY(), block.getZ());
        if (seen.add(key)) {
            snapshots.push(block.getState());
        }
    }

    /** Restores every recorded block in reverse order. Must run on the main thread. */
    public int restore() {
        int restored = snapshots.size();
        while (!snapshots.isEmpty()) {
            BlockState state = snapshots.pop();
            state.update(true, false);
        }
        seen.clear();
        return restored;
    }

    public void clear() {
        snapshots.clear();
        seen.clear();
    }

    public int size() {
        return snapshots.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }
}

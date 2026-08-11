package me.alpha432.corepvp.arena.rollback;

/**
 * Packs a block position into a single {@code long}.
 *
 * <p>Layout: 26 bits X, 12 bits Y, 26 bits Z. That covers the full world border
 * ({@code +/-30,000,000}) and the full build range ({@code -64..319}), and lets
 * the rollback journal use a primitive-keyed map instead of allocating a
 * position object per changed block - of which a single crystal fight produces
 * thousands.
 */
public final class BlockKey {

    /** Minimum Y in modern worlds; also the offset that makes Y unsigned. */
    public static final int MIN_Y = -64;

    private static final long XZ_MASK = 0x3FFFFFFL; // 26 bits
    private static final long Y_MASK = 0xFFFL;      // 12 bits

    private BlockKey() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & XZ_MASK) << 38
                | ((long) (y - MIN_Y) & Y_MASK) << 26
                | ((long) z & XZ_MASK);
    }

    public static int x(long key) {
        // X occupies the top 26 bits, so an arithmetic shift sign-extends it.
        return (int) (key >> 38);
    }

    public static int y(long key) {
        return (int) ((key >>> 26) & Y_MASK) + MIN_Y;
    }

    public static int z(long key) {
        // Push Z's sign bit up to bit 63 first, then shift back down.
        return (int) (key << 38 >> 38);
    }

    public static String toString(long key) {
        return x(key) + "," + y(key) + "," + z(key);
    }
}

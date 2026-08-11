package me.alpha432.corepvp.queue;

/**
 * How far apart two ratings may be to be matched, widening the longer someone
 * has waited.
 */
public record EloRangePolicy(int base, int expansionPerSecond, int max) {

    public static EloRangePolicy defaults() {
        return new EloRangePolicy(50, 25, 5000);
    }

    public int range(long waitedMillis) {
        long seconds = Math.max(0L, waitedMillis) / 1000L;
        long range = base + seconds * expansionPerSecond;
        return (int) Math.min(max, Math.max(0, range));
    }
}

package me.alpha432.corepvp.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloRangePolicyTest {

    private final EloRangePolicy policy = new EloRangePolicy(50, 25, 5000);

    @Test
    void startsAtTheBaseRange() {
        assertEquals(50, policy.range(0L));
        assertEquals(50, policy.range(999L), "sub-second waits do not widen anything");
    }

    @Test
    void widensBySecond() {
        assertEquals(75, policy.range(1_000L));
        assertEquals(300, policy.range(10_000L));
        assertEquals(1550, policy.range(60_000L));
    }

    @Test
    void isClampedAtTheMaximum() {
        assertEquals(5000, policy.range(1_000_000L));
        assertEquals(5000, policy.range(Long.MAX_VALUE / 2));
    }

    @Test
    void neverShrinks() {
        int previous = 0;
        for (long millis = 0L; millis < 400_000L; millis += 3_000L) {
            int range = policy.range(millis);
            assertTrue(range >= previous, "range must be monotonic");
            previous = range;
        }
    }

    @Test
    void negativeWaitsAreTreatedAsZero() {
        assertEquals(50, policy.range(-5_000L));
    }
}

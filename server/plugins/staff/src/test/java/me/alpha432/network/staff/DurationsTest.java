package me.alpha432.network.staff;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationsTest {

    @Test
    void parsesSingleUnits() {
        assertEquals(TimeUnit.SECONDS.toMillis(30), Durations.parse("30s"));
        assertEquals(TimeUnit.MINUTES.toMillis(15), Durations.parse("15m"));
        assertEquals(TimeUnit.HOURS.toMillis(6), Durations.parse("6h"));
        assertEquals(TimeUnit.DAYS.toMillis(7), Durations.parse("7d"));
        assertEquals(TimeUnit.DAYS.toMillis(14), Durations.parse("2w"));
        assertEquals(TimeUnit.DAYS.toMillis(365), Durations.parse("1y"));
    }

    @Test
    void parsesCombinedUnits() {
        assertEquals(TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(12),
                Durations.parse("1d12h"));
        assertEquals(TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(30)
                        + TimeUnit.SECONDS.toMillis(15),
                Durations.parse("2h30m15s"));
    }

    @Test
    void permanentIsZero() {
        assertEquals(0L, Durations.parse("perm"));
        assertEquals(0L, Durations.parse("permanent"));
        assertEquals(0L, Durations.parse("0"));
    }

    @Test
    void rejectsGarbage() {
        assertEquals(-1L, Durations.parse(null));
        assertEquals(-1L, Durations.parse(""));
        assertEquals(-1L, Durations.parse("abc"));
        assertEquals(-1L, Durations.parse("d"));
        assertEquals(-1L, Durations.parse("5x"));
    }

    @Test
    void rejectsNumberWithoutUnit() {
        // A bare "7" is ambiguous, so it must not silently become seven of anything.
        assertEquals(-1L, Durations.parse("7"));
        assertEquals(-1L, Durations.parse("1d30"));
    }

    @Test
    void formatsTheLargestUnitsFirst() {
        assertEquals("2d 4h 15m", Durations.format(
                TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(4) + TimeUnit.MINUTES.toMillis(15)));
        assertEquals("45s", Durations.format(TimeUnit.SECONDS.toMillis(45)));
        assertEquals("0s", Durations.format(0));
        assertEquals("0s", Durations.format(-5));
    }
}

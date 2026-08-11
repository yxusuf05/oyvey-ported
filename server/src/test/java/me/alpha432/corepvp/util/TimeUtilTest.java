package me.alpha432.corepvp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {

    @Test
    void clockPadsSeconds() {
        assertEquals("0:00", TimeUtil.clock(0L));
        assertEquals("0:05", TimeUtil.clock(5_000L));
        assertEquals("1:23", TimeUtil.clock(83_000L));
        assertEquals("10:00", TimeUtil.clock(600_000L));
    }

    @Test
    void clockTreatsNegativeInputAsZero() {
        assertEquals("0:00", TimeUtil.clock(-5_000L));
    }

    @Test
    void compactPicksTheTwoLargestUnits() {
        assertEquals("45s", TimeUtil.compact(45_000L));
        assertEquals("2m 5s", TimeUtil.compact(125_000L));
        assertEquals("1h 2m", TimeUtil.compact(3_725_000L));
        assertEquals("2d 3h", TimeUtil.compact(184_000_000L));
    }

    @Test
    void secondsKeepsOneDecimal() {
        assertEquals("2.4s", TimeUtil.seconds(2_400L));
        assertEquals("0.0s", TimeUtil.seconds(-1L));
    }
}

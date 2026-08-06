package me.alpha432.network.core.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegionTest {

    @Test
    void normalisesSwappedCorners() {
        Region region = new Region("spawn", "lobby", 10, 70, 10, -10, 60, -10);

        assertEquals(-10, region.minX());
        assertEquals(60, region.minY());
        assertEquals(-10, region.minZ());
        assertEquals(10, region.maxX());
        assertEquals(70, region.maxY());
        assertEquals(10, region.maxZ());
    }

    @Test
    void volumeCountsBothEndsInclusive() {
        Region region = new Region("cube", "lobby", 0, 0, 0, 1, 1, 1);

        assertEquals(8L, region.volume());
    }

    @Test
    void flagsDefaultToUnset() {
        Region region = new Region("spawn", "lobby", 0, 0, 0, 5, 5, 5);

        assertNull(region.flag(RegionFlag.PVP));

        region.flag(RegionFlag.PVP, false);
        assertFalse(region.flag(RegionFlag.PVP));

        region.clearFlag(RegionFlag.PVP);
        assertNull(region.flag(RegionFlag.PVP));
    }

    @Test
    void flagKeysRoundTrip() {
        for (RegionFlag flag : RegionFlag.values()) {
            assertEquals(flag, RegionFlag.byKey(flag.key()));
            assertEquals(flag, RegionFlag.byKey(flag.name()));
        }
        assertNull(RegionFlag.byKey("does-not-exist"));
    }

    @Test
    void containsRejectsNullLocation() {
        assertFalse(new Region("spawn", "lobby", 0, 0, 0, 5, 5, 5).contains(null));
    }

    @Test
    void priorityIsFluent() {
        Region region = new Region("spawn", "lobby", 0, 0, 0, 5, 5, 5).priority(7);

        assertEquals(7, region.priority());
        assertTrue(region.flags().isEmpty());
    }
}

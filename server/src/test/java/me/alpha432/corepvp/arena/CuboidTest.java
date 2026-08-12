package me.alpha432.corepvp.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidTest {

    private final Cuboid cuboid = new Cuboid("world", 0, 60, 0, 10, 70, 10);

    @Test
    void cornersAreNormalisedWhicheverWayRound() {
        Cuboid reversed = new Cuboid("world", 10, 70, 10, 0, 60, 0);
        assertEquals(cuboid.minX(), reversed.minX());
        assertEquals(cuboid.maxX(), reversed.maxX());
        assertEquals(cuboid.minY(), reversed.minY());
        assertEquals(cuboid.maxY(), reversed.maxY());
    }

    @Test
    void containsIsInclusiveOnBothCorners() {
        assertTrue(cuboid.contains(0, 60, 0));
        assertTrue(cuboid.contains(10, 70, 10));
        assertTrue(cuboid.contains(5, 65, 5));
        assertFalse(cuboid.contains(-1, 65, 5));
        assertFalse(cuboid.contains(11, 65, 5));
        assertFalse(cuboid.contains(5, 71, 5));
    }

    @Test
    void volumeCountsBlocksNotDistance() {
        // 11 blocks across, not 10: both ends are inside.
        assertEquals(11L * 11L * 11L, cuboid.volume());
        assertEquals(1L, new Cuboid("world", 3, 3, 3, 3, 3, 3).volume());
    }

    @Test
    void expandGrowsHorizontallyAndVerticallyApart() {
        Cuboid bigger = cuboid.expand(2, 5);
        assertEquals(-2, bigger.minX());
        assertEquals(12, bigger.maxX());
        assertEquals(55, bigger.minY());
        assertEquals(75, bigger.maxY());
        assertTrue(bigger.contains(-2, 55, -2));
    }

    @Test
    void survivesASerialisationRoundTrip() {
        Cuboid restored = Cuboid.deserialize(cuboid.serialize());
        assertEquals(cuboid.world(), restored.world());
        assertEquals(cuboid.minX(), restored.minX());
        assertEquals(cuboid.maxZ(), restored.maxZ());
        assertEquals(cuboid.volume(), restored.volume());
    }

    @Test
    void malformedInputDeserialisesToNull() {
        assertNull(Cuboid.deserialize(null));
        assertNull(Cuboid.deserialize(""));
        assertNull(Cuboid.deserialize("world,1,2,3"));
        assertNull(Cuboid.deserialize("world,a,b,c,d,e,f"));
    }

    @Test
    void negativeCoordinatesWork() {
        Cuboid negative = new Cuboid("world", -20, -64, -20, -10, -50, -10);
        assertTrue(negative.contains(-15, -60, -15));
        assertFalse(negative.contains(0, -60, 0));
        assertEquals(11L * 15L * 11L, negative.volume());
    }
}

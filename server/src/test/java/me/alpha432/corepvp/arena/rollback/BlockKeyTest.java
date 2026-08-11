package me.alpha432.corepvp.arena.rollback;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockKeyTest {

    @Test
    void roundTripsTheOrigin() {
        long key = BlockKey.pack(0, 0, 0);
        assertEquals(0, BlockKey.x(key));
        assertEquals(0, BlockKey.y(key));
        assertEquals(0, BlockKey.z(key));
    }

    @Test
    void roundTripsNegativeCoordinates() {
        long key = BlockKey.pack(-1, -64, -1);
        assertEquals(-1, BlockKey.x(key));
        assertEquals(-64, BlockKey.y(key));
        assertEquals(-1, BlockKey.z(key));
    }

    @Test
    void roundTripsTheWorldBorderAndBuildLimits() {
        int[] coordinates = {-30_000_000, -1_000_000, -257, -1, 0, 1, 257, 1_000_000, 30_000_000};
        int[] heights = {-64, -1, 0, 63, 64, 319};
        for (int x : coordinates) {
            for (int z : coordinates) {
                for (int y : heights) {
                    long key = BlockKey.pack(x, y, z);
                    assertEquals(x, BlockKey.x(key), "x at " + x + "," + y + "," + z);
                    assertEquals(y, BlockKey.y(key), "y at " + x + "," + y + "," + z);
                    assertEquals(z, BlockKey.z(key), "z at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void roundTripsRandomPositions() {
        Random random = new Random(42L);
        for (int i = 0; i < 20_000; i++) {
            int x = random.nextInt(-30_000_000, 30_000_001);
            int y = random.nextInt(-64, 320);
            int z = random.nextInt(-30_000_000, 30_000_001);
            long key = BlockKey.pack(x, y, z);
            assertEquals(x, BlockKey.x(key));
            assertEquals(y, BlockKey.y(key));
            assertEquals(z, BlockKey.z(key));
        }
    }

    @Test
    void distinctPositionsProduceDistinctKeys() {
        Set<Long> keys = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -64; y <= -58; y++) {
                for (int z = -3; z <= 3; z++) {
                    assertTrue(keys.add(BlockKey.pack(x, y, z)),
                            "collision at " + x + "," + y + "," + z);
                }
            }
        }
        assertEquals(7 * 7 * 7, keys.size());
    }
}

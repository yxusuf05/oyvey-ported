package me.alpha432.network.practice.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpTerrainGeneratorTest {

    private static final long SEED = 12345L;

    @Test
    void heightStaysWithinTheConfiguredAmplitude() {
        PvpTerrainGenerator generator = new PvpTerrainGenerator(8, 0.012D);

        for (int x = -500; x <= 500; x += 7) {
            for (int z = -500; z <= 500; z += 11) {
                int height = generator.heightAt(SEED, x, z);
                assertTrue(height >= PvpTerrainGenerator.BASE_HEIGHT - 8
                                && height <= PvpTerrainGenerator.BASE_HEIGHT + 8,
                        "height " + height + " at " + x + "/" + z + " left the amplitude");
            }
        }
    }

    @Test
    void sameCoordinateAlwaysGivesTheSameHeight() {
        PvpTerrainGenerator generator = new PvpTerrainGenerator(8, 0.012D);

        assertEquals(generator.heightAt(SEED, 120, -340), generator.heightAt(SEED, 120, -340));
    }

    @Test
    void terrainIsNotFlat() {
        PvpTerrainGenerator generator = new PvpTerrainGenerator(8, 0.012D);
        int first = generator.heightAt(SEED, 0, 0);
        boolean sawDifferent = false;

        for (int x = 1; x < 400 && !sawDifferent; x++) {
            sawDifferent = generator.heightAt(SEED, x, 0) != first;
        }

        assertTrue(sawDifferent, "the PvP terrain has to be uneven, not a plain");
    }

    @Test
    void aFlatterScaleProducesWiderHills() {
        PvpTerrainGenerator steep = new PvpTerrainGenerator(8, 0.05D);
        PvpTerrainGenerator gentle = new PvpTerrainGenerator(8, 0.005D);

        assertTrue(changes(steep) > changes(gentle),
                "a larger scale has to change height more often over the same distance");
    }

    /** How often the height changes along a straight line. */
    private static int changes(PvpTerrainGenerator generator) {
        int count = 0;
        int previous = generator.heightAt(SEED, 0, 0);
        for (int x = 1; x < 500; x++) {
            int height = generator.heightAt(SEED, x, 0);
            if (height != previous) {
                count++;
                previous = height;
            }
        }
        return count;
    }
}

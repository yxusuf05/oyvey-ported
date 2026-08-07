package me.alpha432.network.practice.world;

import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Scatters small oak trees over the PvP terrain. Trees give cover without turning the area into
 * a forest, so the density stays low and they are never placed right next to each other.
 */
public final class TreePopulator extends BlockPopulator {

    private final int treesPerChunk;

    public TreePopulator(int treesPerChunk) {
        this.treesPerChunk = Math.max(0, treesPerChunk);
    }

    @Override
    public void populate(@NotNull WorldInfo world, @NotNull Random random, int chunkX, int chunkZ,
                         @NotNull LimitedRegion region) {
        for (int i = 0; i < treesPerChunk; i++) {
            if (random.nextInt(100) >= 45) {
                continue;
            }
            int x = chunkX * 16 + random.nextInt(16);
            int z = chunkZ * 16 + random.nextInt(16);
            int y = surfaceHeight(region, x, z);
            if (y <= 0) {
                continue;
            }
            plant(region, random, x, y + 1, z);
        }
    }

    /** Walks down from the build limit to the first grass block. */
    private int surfaceHeight(LimitedRegion region, int x, int z) {
        for (int y = PvpTerrainGenerator.BASE_HEIGHT + 40; y > PvpTerrainGenerator.BASE_HEIGHT - 40; y--) {
            if (!region.isInRegion(x, y, z)) {
                continue;
            }
            if (region.getType(x, y, z) == Material.GRASS_BLOCK) {
                return y;
            }
        }
        return -1;
    }

    /** A simple oak: a trunk of four to six logs with a leaf cap around the top. */
    private void plant(LimitedRegion region, Random random, int x, int y, int z) {
        int height = 4 + random.nextInt(3);
        for (int dy = 0; dy < height; dy++) {
            if (!region.isInRegion(x, y + dy, z)) {
                return;
            }
            region.setType(x, y + dy, z, Material.OAK_LOG);
        }
        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int leafX = x + dx;
                    int leafY = top + dy;
                    int leafZ = z + dz;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                        continue;
                    }
                    if (dy == 1 && (Math.abs(dx) > 1 || Math.abs(dz) > 1)) {
                        continue;
                    }
                    if (!region.isInRegion(leafX, leafY, leafZ)) {
                        continue;
                    }
                    if (region.getType(leafX, leafY, leafZ) == Material.AIR) {
                        region.setType(leafX, leafY, leafZ, Material.OAK_LEAVES);
                    }
                }
            }
        }
    }
}

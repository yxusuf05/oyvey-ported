package me.alpha432.network.practice.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Terrain for the PvP wilderness: gentle, walkable hills with a grass surface and nothing that
 * gets in the way of a fight.
 *
 * <p>No caves, no ravines, no water and no ore — a fall into a hole or a swim in a lake decides
 * too many fights. Height comes from a couple of stacked sine waves seeded per world, which is
 * cheap and produces the rolling, uneven ground the area is meant to have. Trees are added by
 * {@link TreePopulator}.
 */
public final class PvpTerrainGenerator extends ChunkGenerator {

    /** Lowest point of the terrain; everything below is solid stone. */
    public static final int BASE_HEIGHT = 64;

    private final int amplitude;
    private final double scale;

    public PvpTerrainGenerator(int amplitude, double scale) {
        this.amplitude = Math.max(1, amplitude);
        this.scale = scale <= 0 ? 0.012D : scale;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo world, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunk) {
        int minY = chunk.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int height = heightAt(world.getSeed(), worldX, worldZ);

                chunk.setBlock(x, minY, z, Material.BEDROCK);
                chunk.setRegion(x, minY + 1, z, x + 1, height - 3, z + 1, Material.STONE);
                chunk.setRegion(x, height - 3, z, x + 1, height, z + 1, Material.DIRT);
                chunk.setBlock(x, height, z, Material.GRASS_BLOCK);
            }
        }
    }

    /**
     * Surface height at a world position. Two waves of different length give small bumps on top
     * of wider hills without needing a full noise implementation.
     */
    public int heightAt(long seed, int x, int z) {
        double offset = (seed % 4096) * 0.0001D;
        double wide = Math.sin((x * scale) + offset) * Math.cos((z * scale) - offset);
        double fine = Math.sin((x * scale * 3.7D) - offset) * Math.cos((z * scale * 3.1D) + offset);
        double combined = wide * 0.75D + fine * 0.25D;
        return BASE_HEIGHT + (int) Math.round(combined * amplitude);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}

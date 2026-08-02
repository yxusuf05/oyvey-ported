package me.blockoutlines.render;

import me.blockoutlines.BlockOutlines;
import me.blockoutlines.config.BlockOutlinesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of where the configured blocks are. Scanning runs on the render thread but only
 * every {@link BlockOutlinesConfig#searchScanInterval} milliseconds, and sections that cannot
 * contain a tracked block are rejected by their palette before a single block is looked at.
 */
public class BlockScanner {
    /** One highlighted region: a box in world space plus the colour it should be drawn in. */
    public record Hit(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color) {
        public double centerX() {
            return (minX + maxX + 1) / 2.0;
        }

        public double centerY() {
            return (minY + maxY + 1) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ + 1) / 2.0;
        }
    }

    private final Map<Block, Integer> tracked = new HashMap<>();
    private List<Hit> hits = List.of();
    private long lastScan;
    private boolean dirty = true;

    /** Forces the block list to be re-read from the config and the world to be re-scanned. */
    public void invalidate() {
        dirty = true;
        lastScan = 0L;
    }

    public List<Hit> hits(ClientLevel level, Vec3 eye) {
        BlockOutlinesConfig config = BlockOutlines.config();
        if (dirty) {
            resolveTrackedBlocks(config);
        }
        if (tracked.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        if (now - lastScan >= config.searchScanInterval) {
            lastScan = now;
            hits = scan(level, eye, config);
        }
        return hits;
    }

    private void resolveTrackedBlocks(BlockOutlinesConfig config) {
        dirty = false;
        tracked.clear();
        config.blocks.forEach((id, color) -> {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) {
                return;
            }
            BuiltInRegistries.BLOCK.getOptional(identifier)
                    .ifPresent(block -> tracked.put(block, color == null ? -1 : color));
        });
        hits = List.of();
    }

    private List<Hit> scan(ClientLevel level, Vec3 eye, BlockOutlinesConfig config) {
        int range = config.searchRange;
        int rangeSq = range * range;
        int centerX = (int) Math.floor(eye.x);
        int centerY = (int) Math.floor(eye.y);
        int centerZ = (int) Math.floor(eye.z);

        int minChunkX = SectionPos.blockToSectionCoord(centerX - range);
        int maxChunkX = SectionPos.blockToSectionCoord(centerX + range);
        int minChunkZ = SectionPos.blockToSectionCoord(centerZ - range);
        int maxChunkZ = SectionPos.blockToSectionCoord(centerZ + range);
        int minY = Math.max(level.getMinY(), centerY - range);
        int maxY = Math.min(level.getMaxY(), centerY + range);

        // Positions grouped by the colour they will be drawn in, so that merging never joins
        // two differently coloured blocks into one box.
        Map<Integer, Set<Long>> byColor = new HashMap<>();
        int found = 0;

        outer:
        for (long packedChunk : chunksByDistance(minChunkX, maxChunkX, minChunkZ, maxChunkZ, centerX, centerZ)) {
            int chunkX = (int) (packedChunk >> 32);
            int chunkZ = (int) packedChunk;

            // null for chunks that are not loaded on the client
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }

            LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                LevelChunkSection section = sections[index];
                int sectionY = chunk.getMinY() + (index << 4);
                if (section.hasOnlyAir() || sectionY + 15 < minY || sectionY > maxY) {
                    continue;
                }
                if (!section.maybeHas(state -> tracked.containsKey(state.getBlock()))) {
                    continue;
                }

                for (int y = 0; y < 16; y++) {
                    int worldY = sectionY + y;
                    if (worldY < minY || worldY > maxY) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        int worldX = SectionPos.sectionToBlockCoord(chunkX) + x;
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            Integer color = tracked.get(state.getBlock());
                            if (color == null) {
                                continue;
                            }

                            int worldZ = SectionPos.sectionToBlockCoord(chunkZ) + z;
                            double dx = worldX + 0.5 - eye.x;
                            double dy = worldY + 0.5 - eye.y;
                            double dz = worldZ + 0.5 - eye.z;
                            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                                continue;
                            }

                            BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                            if (config.searchOnlyExposed && !isExposed(level, pos)) {
                                continue;
                            }

                            int argb = color == -1 ? config.searchColor : color;
                            byColor.computeIfAbsent(argb, key -> new HashSet<>()).add(pos.asLong());
                            if (++found >= config.searchMaxBlocks) {
                                break outer;
                            }
                        }
                    }
                }
            }
        }

        List<Hit> result = new ArrayList<>(found);
        byColor.forEach((color, positions) -> {
            if (config.searchMergeTouching) {
                mergeInto(result, positions, color);
            } else {
                for (long packed : positions) {
                    BlockPos pos = BlockPos.of(packed);
                    result.add(new Hit(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ(), color));
                }
            }
        });
        return result;
    }

    /**
     * Chunk positions in the given rectangle, packed as {@code x << 32 | z} and sorted by their
     * distance to the player. Scanning in that order means the "max blocks" cap keeps the blocks
     * closest to the player instead of whatever corner of the area came first.
     */
    private static List<Long> chunksByDistance(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                               int centerX, int centerZ) {
        int playerChunkX = SectionPos.blockToSectionCoord(centerX);
        int playerChunkZ = SectionPos.blockToSectionCoord(centerZ);

        List<Long> chunks = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
            }
        }
        chunks.sort(java.util.Comparator.comparingInt(packed -> {
            int dx = (int) (packed >> 32) - playerChunkX;
            int dz = (int) (long) packed - playerChunkZ;
            return dx * dx + dz * dz;
        }));
        return chunks;
    }

    private static boolean isExposed(ClientLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.isAir() || !neighbour.canOcclude()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Greedily grows boxes out of the given positions: first along X, then the resulting row
     * along Y, then the resulting slab along Z. Turns an ore vein into a handful of boxes
     * instead of one box per block.
     */
    private static void mergeInto(List<Hit> out, Set<Long> positions, int color) {
        Set<Long> remaining = new HashSet<>(positions);
        List<Long> ordered = new ArrayList<>(positions);
        ordered.sort(null);

        for (long packed : ordered) {
            if (!remaining.remove(packed)) {
                continue;
            }
            BlockPos start = BlockPos.of(packed);
            int minX = start.getX();
            int minY = start.getY();
            int minZ = start.getZ();

            int maxX = minX;
            while (remaining.contains(BlockPos.asLong(maxX + 1, minY, minZ))) {
                remaining.remove(BlockPos.asLong(++maxX, minY, minZ));
            }

            int maxY = minY;
            while (containsAll(remaining, minX, maxX, maxY + 1, maxY + 1, minZ, minZ)) {
                maxY++;
                removeAll(remaining, minX, maxX, maxY, maxY, minZ, minZ);
            }

            int maxZ = minZ;
            while (containsAll(remaining, minX, maxX, minY, maxY, maxZ + 1, maxZ + 1)) {
                maxZ++;
                removeAll(remaining, minX, maxX, minY, maxY, maxZ, maxZ);
            }

            out.add(new Hit(minX, minY, minZ, maxX, maxY, maxZ, color));
        }
    }

    private static boolean containsAll(Set<Long> remaining, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!remaining.contains(BlockPos.asLong(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void removeAll(Set<Long> remaining, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    remaining.remove(BlockPos.asLong(x, y, z));
                }
            }
        }
    }
}

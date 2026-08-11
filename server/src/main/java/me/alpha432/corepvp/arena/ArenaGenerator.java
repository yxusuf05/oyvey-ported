package me.alpha432.corepvp.arena;

import me.alpha432.corepvp.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds arenas from code.
 *
 * <p>This is what makes a fresh server playable without anyone building a map:
 * {@code /corepvp arena generate crystal 4} lays four obsidian arenas out on a
 * grid in the void world and registers them with their spawns and bounds.
 * Placement is deterministic, so the same cell index always produces the same
 * coordinates.
 */
public final class ArenaGenerator {

    /**
     * @param radius      half the platform width in blocks
     * @param floorY      Y of the floor surface
     * @param wallHeight  height of the rim wall around the platform
     * @param buildHeight how far above the floor players may build
     * @param deathDrop   how far below the floor counts as "fell out"
     */
    public record Template(String id, int radius, int floorY, Material floor, Material rim,
                           int wallHeight, int buildHeight, int deathDrop) {
    }

    /** Far enough apart that no explosion or arrow reaches the next arena. */
    private static final int SPACING = 512;
    private static final int COLUMNS = 16;

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        register(new Template("flat", 24, 64, Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, 0, 0, 10));
        register(new Template("sumo", 5, 80, Material.SMOOTH_QUARTZ, Material.QUARTZ_BLOCK, 0, 0, 25));
        register(new Template("build", 24, 64, Material.SMOOTH_STONE, Material.STONE_BRICKS, 0, 30, 10));
        // Obsidian floor with a bedrock rim: the floor is meant to be blown up
        // and rebuilt, the rim is there to stop it being tunnelled through.
        register(new Template("crystal", 20, 64, Material.OBSIDIAN, Material.BEDROCK, 1, 20, 10));
    }

    private static void register(Template template) {
        TEMPLATES.put(template.id(), template);
    }

    public static List<String> templateIds() {
        return List.copyOf(TEMPLATES.keySet());
    }

    public static Template template(String id) {
        return id == null ? null : TEMPLATES.get(id.toLowerCase(Locale.ROOT));
    }

    private record PendingBlock(int x, int y, int z, Material material) {
    }

    private final Plugin plugin;
    private final ArenaManager arenas;
    private final int blocksPerTick;

    public ArenaGenerator(Plugin plugin, ArenaManager arenas, int blocksPerTick) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.blocksPerTick = Math.max(256, blocksPerTick);
    }

    /**
     * Generates {@code count} arenas of a template and registers them.
     *
     * @param progress receives a short status line per finished arena
     * @param onDone   runs on the main thread once everything is placed
     */
    public void generate(Template template, int count, World world,
                         Consumer<String> progress, Runnable onDone) {
        List<PendingBlock> blocks = new ArrayList<>();
        List<Arena> created = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int cell = arenas.takeCell();
            int centerX = (cell % COLUMNS) * SPACING;
            int centerZ = (cell / COLUMNS) * SPACING;
            created.add(layout(template, world, cell, centerX, centerZ, blocks));
        }

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int budget = blocksPerTick;
                while (index < blocks.size() && budget-- > 0) {
                    PendingBlock pending = blocks.get(index++);
                    world.getBlockAt(pending.x(), pending.y(), pending.z())
                            .setBlockData(pending.material().createBlockData(), false);
                }
                if (index < blocks.size()) {
                    return;
                }
                cancel();
                for (Arena arena : created) {
                    arenas.register(arena);
                    progress.accept(arena.id());
                }
                arenas.saveAll();
                Tasks.sync(onDone);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Arena layout(Template template, World world, int cell,
                         int centerX, int centerZ, List<PendingBlock> blocks) {
        int radius = template.radius();
        int floorY = template.floorY();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                boolean edge = x == centerX - radius || x == centerX + radius
                        || z == centerZ - radius || z == centerZ + radius;
                blocks.add(new PendingBlock(x, floorY, z, edge ? template.rim() : template.floor()));
                if (edge) {
                    for (int height = 1; height <= template.wallHeight(); height++) {
                        blocks.add(new PendingBlock(x, floorY + height, z, template.rim()));
                    }
                }
            }
        }

        // Spawns face each other along the X axis. Yaw -90 looks east (+X),
        // yaw 90 looks west (-X).
        Location west = new Location(world, centerX - radius + 3 + 0.5D, floorY + 1, centerZ + 0.5D, -90.0F, 0.0F);
        Location east = new Location(world, centerX + radius - 3 + 0.5D, floorY + 1, centerZ + 0.5D, 90.0F, 0.0F);

        Arena arena = new Arena(template.id() + "_" + cell)
                .template(template.id())
                .addSpawn(west)
                .addSpawn(east)
                .bounds(new Cuboid(world.getName(),
                        centerX - radius - 3, floorY - template.deathDrop() - 5, centerZ - radius - 3,
                        centerX + radius + 3, floorY + 60, centerZ + radius + 3))
                .deathY(floorY - template.deathDrop());

        if (template.buildHeight() > 0) {
            arena.buildBounds(new Cuboid(world.getName(),
                    centerX - radius, floorY, centerZ - radius,
                    centerX + radius, floorY + template.buildHeight(), centerZ + radius));
        }
        return arena;
    }
}

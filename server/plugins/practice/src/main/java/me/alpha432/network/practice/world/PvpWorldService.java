package me.alpha432.network.practice.world;

import me.alpha432.network.core.teleport.RandomTeleportService;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.core.world.WorldService;
import me.alpha432.network.practice.PracticePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The PvP wilderness of the practice area: an own world with hilly terrain and trees, no caves,
 * no water and no mobs. {@code /rtp} drops players somewhere in it.
 */
public final class PvpWorldService implements RandomTeleportService.Provider {

    private final PracticePlugin plugin;
    private World world;

    public PvpWorldService(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public String worldName() {
        return plugin.getConfig().getString("pvp-world.name", "practice_pvp");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("pvp-world.enabled", true);
    }

    /** Creates the world on startup. Safe to call twice. */
    public void load() {
        if (!isEnabled()) {
            return;
        }
        World existing = Bukkit.getWorld(worldName());
        if (existing != null) {
            world = existing;
            applySettings(world);
            return;
        }
        WorldCreator creator = new WorldCreator(worldName())
                .environment(World.Environment.NORMAL)
                .generator(new PvpTerrainGenerator(
                        plugin.getConfig().getInt("pvp-world.hill-height", 8),
                        plugin.getConfig().getDouble("pvp-world.hill-scale", 0.012D)))
                .generateStructures(false);
        plugin.getLogger().info("Creating the PvP world " + worldName() + "…");
        world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().severe("Bukkit refused to create the PvP world " + worldName());
            return;
        }
        world.getPopulators().add(new TreePopulator(
                plugin.getConfig().getInt("pvp-world.trees-per-chunk", 2)));
        applySettings(world);
    }

    /** Nothing in this world should distract from a fight. */
    private void applySettings(World world) {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(true);
        world.setTime(6000L);
        world.setStorm(false);
        WorldService.applyGameRule(world, "doDaylightCycle", "false");
        WorldService.applyGameRule(world, "doWeatherCycle", "false");
        WorldService.applyGameRule(world, "doMobSpawning", "false");
        WorldService.applyGameRule(world, "keepInventory", "true");
        WorldService.applyGameRule(world, "announceAdvancements", "false");
        WorldService.applyGameRule(world, "naturalRegeneration", "false");
    }

    public World world() {
        return world;
    }

    @Override
    public boolean handles(World world) {
        return this.world != null && this.world.equals(world);
    }

    @Override
    public CompletableFuture<Location> find(Player player) {
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        int radius = plugin.getConfig().getInt("pvp-world.rtp-radius", 2000);
        int x = ThreadLocalRandom.current().nextInt(-radius, radius);
        int z = ThreadLocalRandom.current().nextInt(-radius, radius);
        Location column = new Location(world, x + 0.5, PvpTerrainGenerator.BASE_HEIGHT, z + 0.5);

        // The terrain has no caves or water, so the highest block is always a fine landing spot.
        return world.getChunkAtAsync(column).thenApply(chunk -> {
            Location surface = world.getHighestBlockAt(column).getLocation().add(0.5, 1, 0.5);
            surface.setYaw(player.getLocation().getYaw());
            return Locations.findSafe(surface);
        });
    }

    @Override
    public String searchingKey() {
        return "rtp.searching";
    }
}

package me.alpha432.network.smp.teleport;

import me.alpha432.network.core.teleport.RandomTeleportService;
import me.alpha432.network.core.util.Locations;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** {@code /rtp} in the survival worlds: a random spot in the wilderness. */
public final class SmpRandomTeleport implements RandomTeleportService.Provider {

    private final SmpPlugin plugin;

    public SmpRandomTeleport(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean handles(World world) {
        return plugin.spawn().isSmp(world);
    }

    @Override
    public CompletableFuture<Location> find(Player player) {
        World world = player.getWorld();
        int minRadius = plugin.getConfig().getInt("rtp.min-radius", 500);
        int maxRadius = plugin.getConfig().getInt("rtp.radius", 5000);
        int attempts = plugin.getConfig().getInt("rtp.attempts", 5);
        return search(world, minRadius, maxRadius, attempts);
    }

    /** Loads a candidate chunk, checks the surface and retries a few times before giving up. */
    private CompletableFuture<Location> search(World world, int minRadius, int maxRadius, int attempts) {
        if (attempts <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        Location candidate = randomColumn(world, minRadius, maxRadius);
        return world.getChunkAtAsync(candidate).thenCompose(chunk -> {
            Location surface = world.getHighestBlockAt(candidate).getLocation().add(0.5, 1, 0.5);
            if (isAcceptable(surface)) {
                return CompletableFuture.completedFuture(Locations.findSafe(surface));
            }
            return search(world, minRadius, maxRadius, attempts - 1);
        });
    }

    /** Water and lava make for a bad arrival point. */
    private static boolean isAcceptable(Location surface) {
        var below = surface.clone().subtract(0, 1, 0).getBlock();
        return !below.isLiquid() && below.getType().isSolid();
    }

    private static Location randomColumn(World world, int minRadius, int maxRadius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int distance = random.nextInt(minRadius, Math.max(minRadius + 1, maxRadius));
        double angle = random.nextDouble() * Math.PI * 2;
        return new Location(world,
                Math.cos(angle) * distance, world.getSeaLevel(), Math.sin(angle) * distance);
    }
}

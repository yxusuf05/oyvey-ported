package me.alpha432.network.core.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Registry behind {@code /rtp}. Every area registers its own provider, so the same command
 * drops players into the survival wilderness in the SMP and into the PvP terrain in practice.
 */
public final class RandomTeleportService {

    /** Supplies a random destination for the worlds it claims. */
    public interface Provider {

        /** Whether this provider is responsible for the world the player stands in. */
        boolean handles(World world);

        /**
         * Looks for a destination. May load chunks, so it returns a future that completes on
         * the main thread. Completing with {@code null} means "nothing found".
         */
        CompletableFuture<Location> find(Player player);

        /** Message key sent while the search runs. */
        default String searchingKey() {
            return "rtp.searching";
        }
    }

    private final List<Provider> providers = new ArrayList<>();

    public void register(Provider provider) {
        providers.add(provider);
    }

    public void unregister(Provider provider) {
        providers.remove(provider);
    }

    /** The provider responsible for this world, or {@code null}. */
    public Provider providerFor(World world) {
        for (Provider provider : providers) {
            if (provider.handles(world)) {
                return provider;
            }
        }
        return null;
    }

    public boolean hasProvider(World world) {
        return providerFor(world) != null;
    }
}

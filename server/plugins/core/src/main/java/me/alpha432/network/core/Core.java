package me.alpha432.network.core;

import me.alpha432.network.core.board.BoardService;
import me.alpha432.network.core.board.TabService;
import me.alpha432.network.core.economy.EconomyService;
import me.alpha432.network.core.profile.ProfileService;
import me.alpha432.network.core.rank.RankService;
import me.alpha432.network.core.region.RegionService;
import me.alpha432.network.core.storage.Database;
import me.alpha432.network.core.teleport.TeleportService;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.core.world.WorldService;

/**
 * Static entry point to the shared services. The feature plugins declare NetworkCore as a
 * dependency, so this class is on their classpath at runtime.
 */
public final class Core {

    private static CorePlugin plugin;

    private Core() {
    }

    static void bind(CorePlugin instance) {
        plugin = instance;
    }

    public static boolean isReady() {
        return plugin != null;
    }

    public static CorePlugin plugin() {
        if (plugin == null) {
            throw new IllegalStateException("NetworkCore is not enabled yet");
        }
        return plugin;
    }

    public static Messages messages() {
        return plugin().messages();
    }

    public static Database database() {
        return plugin().database();
    }

    public static ProfileService profiles() {
        return plugin().profiles();
    }

    public static RankService ranks() {
        return plugin().ranks();
    }

    public static EconomyService economy() {
        return plugin().economy();
    }

    public static WorldService worlds() {
        return plugin().worlds();
    }

    public static TeleportService teleports() {
        return plugin().teleports();
    }

    public static RegionService regions() {
        return plugin().regions();
    }

    public static BoardService boards() {
        return plugin().boards();
    }

    public static TabService tabs() {
        return plugin().tabs();
    }
}

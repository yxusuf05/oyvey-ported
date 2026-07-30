package dev.smoothinv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SmoothInvConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SmoothInvConfig instance = new SmoothInvConfig();

    /**
     * Reuse tooltip lines instead of rebuilding them every frame.
     * Biggest win against GC stutter with NBT-heavy items (shulkers, enchants).
     */
    public boolean cacheTooltips = true;

    /**
     * How long a cached tooltip stays valid. Lower = more up to date,
     * higher = fewer allocations. 250 ms is invisible in practice.
     */
    public int tooltipCacheMs = 250;

    /**
     * Skip rendering the 3D player model in the inventory screen.
     * Off by default because it changes how the screen looks.
     */
    public boolean hidePlayerModelInInventory = false;

    public static SmoothInvConfig get() {
        return instance;
    }

    public static void load() {
        Path file = configFile();
        if (Files.exists(file)) {
            try {
                instance = GSON.fromJson(Files.readString(file), SmoothInvConfig.class);
                if (instance == null) instance = new SmoothInvConfig();
            } catch (Exception e) {
                System.err.println("[SmoothInv] Could not read config, using defaults: " + e);
                instance = new SmoothInvConfig();
            }
        }
        instance.tooltipCacheMs = Math.max(0, Math.min(instance.tooltipCacheMs, 5000));
        save();
    }

    public static void save() {
        try {
            Files.writeString(configFile(), GSON.toJson(instance));
        } catch (IOException e) {
            System.err.println("[SmoothInv] Could not save config: " + e);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("smoothinv.json");
    }
}

package me.alpha432.network.core.world;

import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** One entry of {@code worlds.yml}. */
public record WorldDefinition(String name,
                              World.Environment environment,
                              boolean voidWorld,
                              Difficulty difficulty,
                              boolean pvp,
                              Long seed,
                              Map<String, String> gameRules,
                              boolean platform,
                              Material platformMaterial,
                              int platformRadius,
                              int platformY) {

    public static WorldDefinition from(String name, ConfigurationSection section) {
        ConfigurationSection platform = section.getConfigurationSection("platform");
        Map<String, String> rules = new LinkedHashMap<>();
        ConfigurationSection gameRules = section.getConfigurationSection("gamerules");
        if (gameRules != null) {
            for (String key : gameRules.getKeys(false)) {
                rules.put(key, String.valueOf(gameRules.get(key)));
            }
        }
        return new WorldDefinition(
                name,
                environment(section.getString("environment", "NORMAL")),
                section.getBoolean("void", false),
                difficulty(section.getString("difficulty", "NORMAL")),
                section.getBoolean("pvp", true),
                section.contains("seed") ? section.getLong("seed") : null,
                rules,
                platform != null && platform.getBoolean("enabled", false),
                material(platform == null ? "STONE" : platform.getString("material", "STONE")),
                platform == null ? 8 : platform.getInt("radius", 8),
                platform == null ? 64 : platform.getInt("y", 64));
    }

    private static World.Environment environment(String raw) {
        try {
            return World.Environment.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return World.Environment.NORMAL;
        }
    }

    private static Difficulty difficulty(String raw) {
        try {
            return Difficulty.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Difficulty.NORMAL;
        }
    }

    private static Material material(String raw) {
        Material material = Material.matchMaterial(raw);
        return material == null ? Material.STONE : material;
    }
}

package me.alpha432.network.core.world;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Creates and configures the worlds of the network from {@code worlds.yml}. */
public final class WorldService {

    private final Plugin plugin;
    private final Map<String, WorldDefinition> definitions = new LinkedHashMap<>();

    public WorldService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        if (!file.exists()) {
            plugin.saveResource("worlds.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        definitions.clear();
        ConfigurationSection section = config.getConfigurationSection("worlds");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection world = section.getConfigurationSection(name);
            if (world != null) {
                definitions.put(name, WorldDefinition.from(name, world));
            }
        }
    }

    /** Loads or creates every configured world. Called once during startup. */
    public void loadAll() {
        for (WorldDefinition definition : definitions.values()) {
            try {
                loadOrCreate(definition);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Could not load world " + definition.name() + ": " + e.getMessage());
            }
        }
    }

    public World loadOrCreate(WorldDefinition definition) {
        World existing = Bukkit.getWorld(definition.name());
        boolean fresh = existing == null && !new File(Bukkit.getWorldContainer(), definition.name()).isDirectory();

        World world = existing;
        if (world == null) {
            WorldCreator creator = new WorldCreator(definition.name())
                    .environment(definition.environment());
            if (definition.voidWorld()) {
                creator.generator(new VoidGenerator());
                creator.generateStructures(false);
            }
            if (definition.seed() != null) {
                creator.seed(definition.seed());
            }
            plugin.getLogger().info("Loading world " + definition.name() + (fresh ? " (new)" : ""));
            world = creator.createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("Bukkit refused to create world " + definition.name());
        }

        world.setDifficulty(definition.difficulty());
        world.setPVP(definition.pvp());
        applyGameRules(world, definition.gameRules());

        if (fresh && definition.platform()) {
            buildPlatform(world, definition);
        }
        return world;
    }

    // GameRule.getByName is deprecated in favour of the registry, but it is the only lookup that
    // takes the vanilla names people write in worlds.yml.
    @SuppressWarnings({"deprecation", "removal"})
    private void applyGameRules(World world, Map<String, String> rules) {
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            GameRule<?> rule = GameRule.getByName(entry.getKey());
            if (rule == null) {
                plugin.getLogger().warning("Unknown gamerule " + entry.getKey() + " for world " + world.getName());
                continue;
            }
            try {
                if (rule.getType() == Boolean.class) {
                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                    world.setGameRule(booleanRule, Boolean.parseBoolean(entry.getValue()));
                } else {
                    @SuppressWarnings("unchecked")
                    GameRule<Integer> integerRule = (GameRule<Integer>) rule;
                    world.setGameRule(integerRule, Integer.parseInt(entry.getValue()));
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid value for gamerule " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    /** Void worlds need something to stand on before anyone can build there. */
    private void buildPlatform(World world, WorldDefinition definition) {
        int radius = Math.max(1, definition.platformRadius());
        int y = definition.platformY();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, y, z).setType(definition.platformMaterial(), false);
            }
        }
        world.setSpawnLocation(new Location(world, 0.5, y + 1, 0.5));
        plugin.getLogger().info("Built a " + (radius * 2 + 1) + "x" + (radius * 2 + 1)
                + " spawn platform in world " + world.getName());
    }

    public Optional<World> world(String name) {
        return Optional.ofNullable(Bukkit.getWorld(name));
    }

    public boolean isManaged(String name) {
        return definitions.containsKey(name);
    }

    public Map<String, WorldDefinition> definitions() {
        return definitions;
    }
}

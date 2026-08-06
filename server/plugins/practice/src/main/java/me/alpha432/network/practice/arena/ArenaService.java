package me.alpha432.network.practice.arena;

import me.alpha432.network.core.region.Region;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Loads {@code arenas.yml} and hands out free arenas to starting matches. */
public final class ArenaService {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Location first = Locations.deserialize(section.getString("spawn-1"));
            Location second = Locations.deserialize(section.getString("spawn-2"));
            Region bounds = null;
            ConfigurationSection area = section.getConfigurationSection("bounds");
            if (area != null) {
                bounds = new Region("arena_" + name, area.getString("world", "practice"),
                        area.getInt("min-x"), area.getInt("min-y"), area.getInt("min-z"),
                        area.getInt("max-x"), area.getInt("max-y"), area.getInt("max-z"));
            }
            List<String> kits = new ArrayList<>();
            for (String kit : section.getStringList("kits")) {
                kits.add(kit.toLowerCase(Locale.ROOT));
            }
            arenas.put(name.toLowerCase(Locale.ROOT), new Arena(
                    name.toLowerCase(Locale.ROOT),
                    section.getString("display-name", name),
                    first, second, bounds, kits,
                    section.getBoolean("enabled", true)));
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String path = "arenas." + arena.name();
            config.set(path + ".display-name", arena.displayName());
            config.set(path + ".spawn-1", Locations.serialize(arena.firstSpawn()));
            config.set(path + ".spawn-2", Locations.serialize(arena.secondSpawn()));
            config.set(path + ".kits", arena.kits());
            config.set(path + ".enabled", arena.isEnabled());
            Region bounds = arena.bounds();
            if (bounds != null) {
                config.set(path + ".bounds.world", bounds.world());
                config.set(path + ".bounds.min-x", bounds.minX());
                config.set(path + ".bounds.min-y", bounds.minY());
                config.set(path + ".bounds.min-z", bounds.minZ());
                config.set(path + ".bounds.max-x", bounds.maxX());
                config.set(path + ".bounds.max-y", bounds.maxY());
                config.set(path + ".bounds.max-z", bounds.maxZ());
            }
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    public Optional<Arena> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(arenas.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public List<String> names() {
        return new ArrayList<>(arenas.keySet());
    }

    public Arena create(String name) {
        Arena arena = new Arena(name.toLowerCase(Locale.ROOT), name, null, null, null,
                new ArrayList<>(), true);
        arenas.put(arena.name(), arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        boolean removed = arenas.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** Every arena that could host this kit, whether it is busy or not. */
    public List<Arena> supporting(String kitId) {
        List<Arena> result = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.isEnabled() && arena.isComplete() && arena.supports(kitId)) {
                result.add(arena);
            }
        }
        return result;
    }

    /**
     * Reserves a free arena for the kit.
     *
     * @param preferred name of a specific map, or {@code null} for a random one
     * @return the reserved arena, or empty when every fitting arena is busy
     */
    public Optional<Arena> reserve(String kitId, String preferred) {
        if (preferred != null) {
            Arena arena = arenas.get(preferred.toLowerCase(Locale.ROOT));
            if (arena != null && arena.isAvailableFor(kitId)) {
                arena.occupied(true);
                return Optional.of(arena);
            }
            return Optional.empty();
        }
        List<Arena> free = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.isAvailableFor(kitId)) {
                free.add(arena);
            }
        }
        if (free.isEmpty()) {
            return Optional.empty();
        }
        Arena chosen = free.get(ThreadLocalRandom.current().nextInt(free.size()));
        chosen.occupied(true);
        return Optional.of(chosen);
    }

    public void release(Arena arena) {
        arena.occupied(false);
    }

    /** Frees every arena, e.g. when the plugin shuts down. */
    public void releaseAll() {
        arenas.values().forEach(arena -> arena.occupied(false));
    }
}

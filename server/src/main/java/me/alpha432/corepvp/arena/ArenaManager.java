package me.alpha432.corepvp.arena;

import me.alpha432.corepvp.arena.rollback.RollbackService;
import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.YamlFile;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.util.Locations;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The arena registry and the allocator that hands them to matches. */
public final class ArenaManager {

    private static final String FILE = "arenas.yml";

    private final Plugin plugin;
    private final ConfigManager configs;
    private final RollbackService rollback;

    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, Arena> byPlayer = new ConcurrentHashMap<>();
    private int nextCell;

    public ArenaManager(Plugin plugin, ConfigManager configs, RollbackService rollback) {
        this.plugin = plugin;
        this.configs = configs;
        this.rollback = rollback;
    }

    public void load() {
        arenas.clear();
        YamlFile file = configs.file(FILE);
        nextCell = file.get().getInt("next-cell", 0);

        ConfigurationSection root = file.get().getConfigurationSection("arenas");
        if (root == null) {
            plugin.getLogger().info("No arenas configured yet - use /corepvp arena generate <template> <count>.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Arena arena = new Arena(id);
            arena.template(section.getString("template", "flat"));
            arena.bounds(Cuboid.deserialize(section.getString("bounds")));
            arena.buildBounds(Cuboid.deserialize(section.getString("build-bounds")));
            if (section.contains("death-y")) {
                arena.deathY(section.getInt("death-y"));
            }
            arena.allowedKits(new LinkedHashSet<>(section.getStringList("allowed-kits")));
            if (!section.getBoolean("enabled", true)) {
                arena.state(ArenaState.DISABLED);
            }

            List<Location> spawns = new ArrayList<>();
            for (String raw : section.getStringList("spawns")) {
                Location spawn = Locations.deserialize(raw);
                if (spawn != null) {
                    spawns.add(spawn);
                }
            }
            arena.spawns(spawns);

            if (arena.bounds() == null || spawns.size() < 2) {
                plugin.getLogger().warning("Arena '" + id + "' is incomplete (needs bounds and two spawns) - skipping.");
                continue;
            }
            arenas.put(arena.id(), arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    public void saveAll() {
        YamlFile file = configs.file(FILE);
        file.get().set("next-cell", nextCell);
        file.get().set("arenas", null);
        ConfigurationSection root = file.get().createSection("arenas");
        for (Arena arena : arenas.values()) {
            save(root.createSection(arena.id()), arena);
        }
        file.save();
    }

    private void save(ConfigurationSection section, Arena arena) {
        section.set("template", arena.template());
        section.set("bounds", arena.bounds() == null ? null : arena.bounds().serialize());
        // The raw field, not the getter: the getter falls back to the arena
        // bounds, which would persist a build area the arena never declared.
        section.set("build-bounds", arena.rawBuildBounds() == null ? null : arena.rawBuildBounds().serialize());
        section.set("death-y", arena.deathY());
        section.set("enabled", arena.state() != ArenaState.DISABLED);
        section.set("allowed-kits", new ArrayList<>(arena.allowedKits()));
        List<String> spawns = new ArrayList<>();
        for (Location spawn : arena.spawns()) {
            spawns.add(Locations.serialize(spawn));
        }
        section.set("spawns", spawns);
    }

    /**
     * Takes an arena out of the pool for a match.
     *
     * <p>Synchronised and marking the arena {@code IN_USE} in the same step, so
     * two matches starting in the same tick can never be handed the same arena.
     *
     * @return an arena, or null when none are free - callers must check and
     *         leave the players queued rather than consuming their entries
     */
    public synchronized Arena acquire(Kit kit) {
        List<Arena> candidates = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.available() && arena.supports(kit)) {
                candidates.add(arena);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Arena arena = candidates.get((int) (Math.random() * candidates.size()));
        arena.state(ArenaState.IN_USE);
        return arena;
    }

    /** Returns an arena to the pool once its blocks have been restored. */
    public void release(Arena arena) {
        if (arena.state() == ArenaState.DISABLED) {
            return;
        }
        arena.state(ArenaState.RESETTING);
        rollback.restore(arena, () -> arena.state(ArenaState.AVAILABLE));
    }

    /** Used on shutdown: restores every arena on the calling thread. */
    public void restoreAllBlocking() {
        for (Arena arena : arenas.values()) {
            if (!arena.journal().isEmpty()) {
                rollback.restoreBlocking(arena);
            }
            if (arena.state() != ArenaState.DISABLED) {
                arena.state(ArenaState.AVAILABLE);
            }
        }
        byPlayer.clear();
    }

    public void track(Player player, Arena arena) {
        byPlayer.put(player.getUniqueId(), arena);
    }

    public void untrack(Player player) {
        byPlayer.remove(player.getUniqueId());
    }

    /** The arena a player is currently fighting in, or null. */
    public Arena arenaOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    /**
     * The in-use arena containing a location, or null.
     *
     * <p>Explosions do not come with a player attached, so block changes from
     * them have to be resolved by position. Only arenas that are actually in a
     * match are scanned, which keeps this to a handful of bounds checks.
     */
    public Arena arenaAt(Location location) {
        if (location == null) {
            return null;
        }
        for (Arena arena : arenas.values()) {
            if (arena.state() == ArenaState.IN_USE && arena.bounds() != null
                    && arena.bounds().contains(location)) {
                return arena;
            }
        }
        return null;
    }

    public Arena byId(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public void register(Arena arena) {
        arenas.put(arena.id(), arena);
    }

    public boolean remove(String id) {
        return arenas.remove(id.toLowerCase(Locale.ROOT)) != null;
    }

    public List<Arena> all() {
        return List.copyOf(arenas.values());
    }

    public int count() {
        return arenas.size();
    }

    public long availableCount(Kit kit) {
        return arenas.values().stream().filter(arena -> arena.available() && arena.supports(kit)).count();
    }

    public int nextCell() {
        return nextCell;
    }

    public int takeCell() {
        return nextCell++;
    }
}

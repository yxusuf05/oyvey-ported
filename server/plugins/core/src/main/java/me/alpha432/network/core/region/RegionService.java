package me.alpha432.network.core.region;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
import java.util.logging.Level;

/**
 * Holds the protected cuboids of the network. Lookups walk the region list, which is fine for
 * the handful of regions a server like this has.
 */
public final class RegionService {

    public static final String BYPASS_PERMISSION = "network.region.bypass";

    private final Plugin plugin;
    private final File file;
    private final Map<String, Region> regions = new LinkedHashMap<>();

    public RegionService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
        load();
    }

    public void load() {
        regions.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("regions");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Region region = new Region(name, section.getString("world", "world"),
                    section.getInt("min-x"), section.getInt("min-y"), section.getInt("min-z"),
                    section.getInt("max-x"), section.getInt("max-y"), section.getInt("max-z"));
            region.priority(section.getInt("priority", 0));
            ConfigurationSection flags = section.getConfigurationSection("flags");
            if (flags != null) {
                for (String key : flags.getKeys(false)) {
                    RegionFlag flag = RegionFlag.byKey(key);
                    if (flag != null) {
                        region.flag(flag, flags.getBoolean(key));
                    }
                }
            }
            regions.put(name.toLowerCase(Locale.ROOT), region);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Region region : regions.values()) {
            String path = "regions." + region.name();
            config.set(path + ".world", region.world());
            config.set(path + ".min-x", region.minX());
            config.set(path + ".min-y", region.minY());
            config.set(path + ".min-z", region.minZ());
            config.set(path + ".max-x", region.maxX());
            config.set(path + ".max-y", region.maxY());
            config.set(path + ".max-z", region.maxZ());
            config.set(path + ".priority", region.priority());
            for (Map.Entry<RegionFlag, Boolean> entry : region.flags().entrySet()) {
                config.set(path + ".flags." + entry.getKey().key(), entry.getValue());
            }
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save regions.yml", e);
        }
    }

    public void add(Region region) {
        regions.put(region.name().toLowerCase(Locale.ROOT), region);
        save();
    }

    public boolean remove(String name) {
        boolean removed = regions.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<Region> get(String name) {
        return Optional.ofNullable(regions.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<Region> all() {
        return regions.values();
    }

    /** Regions covering the location, highest priority first. */
    public List<Region> at(Location location) {
        List<Region> found = new ArrayList<>();
        for (Region region : regions.values()) {
            if (region.contains(location)) {
                found.add(region);
            }
        }
        found.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return found;
    }

    /** The highest priority region that sets the flag decides. Unset means allowed. */
    public boolean allows(Location location, RegionFlag flag) {
        for (Region region : at(location)) {
            Boolean value = region.flag(flag);
            if (value != null) {
                return value;
            }
        }
        return true;
    }

    /** Same as {@link #allows(Location, RegionFlag)} but staff with the bypass permission win. */
    public boolean allows(Player player, Location location, RegionFlag flag) {
        if (player != null && player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }
        return allows(location, flag);
    }
}

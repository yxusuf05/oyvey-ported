package me.alpha432.network.smp.warp;

import me.alpha432.network.core.util.Locations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
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
import java.util.logging.Level;

/** Server wide warp points, stored in {@code warps.yml}. */
public final class WarpService {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Warp> warps = new LinkedHashMap<>();

    public WarpService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
        load();
    }

    public void load() {
        warps.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("warps");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Location location = Locations.deserialize(section.getString("location"));
            if (location == null) {
                plugin.getLogger().warning("Warp " + name + " points into a world that is not loaded.");
                continue;
            }
            Material icon = Material.matchMaterial(section.getString("icon", "ENDER_PEARL"));
            warps.put(name.toLowerCase(Locale.ROOT), new Warp(
                    name,
                    location,
                    section.getString("permission"),
                    section.getString("description", ""),
                    icon == null ? Material.ENDER_PEARL : icon));
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Warp warp : warps.values()) {
            String path = "warps." + warp.name();
            config.set(path + ".location", Locations.serialize(warp.location()));
            config.set(path + ".permission", warp.permission());
            config.set(path + ".description", warp.description());
            config.set(path + ".icon", warp.icon().name());
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save warps.yml", e);
        }
    }

    public Optional<Warp> get(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase(Locale.ROOT)));
    }

    public Collection<Warp> all() {
        return warps.values();
    }

    /** Warps the sender is allowed to use. */
    public List<Warp> visibleTo(CommandSender sender) {
        List<Warp> visible = new ArrayList<>();
        for (Warp warp : warps.values()) {
            if (warp.permission() == null || sender.hasPermission(warp.permission())) {
                visible.add(warp);
            }
        }
        return visible;
    }

    public void set(String name, Location location) {
        Warp existing = warps.get(name.toLowerCase(Locale.ROOT));
        warps.put(name.toLowerCase(Locale.ROOT), new Warp(
                name,
                location.clone(),
                existing == null ? null : existing.permission(),
                existing == null ? "" : existing.description(),
                existing == null ? Material.ENDER_PEARL : existing.icon()));
        save();
    }

    public boolean delete(String name) {
        boolean removed = warps.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public List<String> names() {
        return new ArrayList<>(warps.keySet());
    }
}

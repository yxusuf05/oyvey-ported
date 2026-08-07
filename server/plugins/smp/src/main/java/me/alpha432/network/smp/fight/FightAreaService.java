package me.alpha432.network.smp.fight;

import me.alpha432.network.core.region.Region;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/** Loads {@code fightareas.yml} and runs the reset timer of the crystal areas. */
public final class FightAreaService {

    private final Plugin plugin;
    private final File file;
    private final Map<String, FightArea> areas = new LinkedHashMap<>();
    private BukkitTask resetTask;

    public FightAreaService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fightareas.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("fightareas.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        areas.clear();
        ConfigurationSection root = config.getConfigurationSection("areas");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            FightArea.Type type;
            try {
                type = FightArea.Type.valueOf(section.getString("type", "SWORD").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown fight area type in " + name + ", using SWORD.");
                type = FightArea.Type.SWORD;
            }

            Region region = null;
            ConfigurationSection bounds = section.getConfigurationSection("bounds");
            if (bounds != null) {
                region = new Region("fight_" + name, bounds.getString("world", "smp"),
                        bounds.getInt("min-x"), bounds.getInt("min-y"), bounds.getInt("min-z"),
                        bounds.getInt("max-x"), bounds.getInt("max-y"), bounds.getInt("max-z"));
            }

            Set<Material> allowed = EnumSet.noneOf(Material.class);
            for (String entry : section.getStringList("allowed-items")) {
                Material material = Material.matchMaterial(entry);
                if (material == null) {
                    plugin.getLogger().warning("Unknown material " + entry + " in fight area " + name);
                    continue;
                }
                allowed.add(material);
            }

            areas.put(name.toLowerCase(Locale.ROOT), new FightArea(
                    name.toLowerCase(Locale.ROOT),
                    type,
                    region,
                    allowed,
                    section.getInt("reset-seconds", 0),
                    Locations.deserialize(section.getString("spawn"))));
        }
    }

    /** Writes back what admins change in game: the bounds and the spawn. */
    public void save() {
        YamlConfiguration config = file.exists()
                ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        for (FightArea area : areas.values()) {
            String path = "areas." + area.name();
            config.set(path + ".type", area.type().name());
            config.set(path + ".reset-seconds", area.resetSeconds());
            config.set(path + ".spawn", Locations.serialize(area.spawn()));
            List<String> allowed = new ArrayList<>();
            area.allowedItems().forEach(material -> allowed.add(material.name()));
            config.set(path + ".allowed-items", allowed);
            Region region = area.region();
            if (region != null) {
                config.set(path + ".bounds.world", region.world());
                config.set(path + ".bounds.min-x", region.minX());
                config.set(path + ".bounds.min-y", region.minY());
                config.set(path + ".bounds.min-z", region.minZ());
                config.set(path + ".bounds.max-x", region.maxX());
                config.set(path + ".bounds.max-y", region.maxY());
                config.set(path + ".bounds.max-z", region.maxZ());
            }
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save fightareas.yml", e);
        }
    }

    /** Checks every second whether an area is due for its reset. */
    public void startResetTask() {
        stopResetTask();
        resetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (FightArea area : areas.values()) {
                if (!area.isResetDue()) {
                    continue;
                }
                int restored = area.reset();
                if (restored > 0) {
                    plugin.getLogger().fine("Reset fight area " + area.name()
                            + " (" + restored + " blocks)");
                    announce(area, restored);
                }
            }
        }, 20L, 20L);
    }

    private void announce(FightArea area, int restored) {
        Region region = area.region();
        if (region == null) {
            return;
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            if (area.contains(player.getLocation())) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Arena zurückgesetzt (" + restored + " Blöcke)"));
            }
        }
    }

    public void stopResetTask() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    /** Resets every area, e.g. on shutdown, so no player-built blocks are left behind. */
    public void resetAll() {
        areas.values().forEach(FightArea::reset);
    }

    public Optional<FightArea> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(areas.get(name.toLowerCase(Locale.ROOT)));
    }

    /** The area covering this position, or empty. */
    public Optional<FightArea> at(Location location) {
        for (FightArea area : areas.values()) {
            if (area.contains(location)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    public Collection<FightArea> all() {
        return areas.values();
    }

    public List<String> names() {
        return new ArrayList<>(areas.keySet());
    }
}

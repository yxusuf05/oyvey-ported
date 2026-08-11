package me.alpha432.corepvp.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns every YAML file the plugin reads, so a reload is one call. */
public final class ConfigManager {

    private final Plugin plugin;
    private final Map<String, YamlFile> files = new LinkedHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Loads (and if needed creates) a YAML file, caching it by name. */
    public YamlFile file(String name) {
        return files.computeIfAbsent(name, key -> new YamlFile(plugin, key));
    }

    public YamlConfiguration get(String name) {
        return file(name).get();
    }

    public YamlConfiguration main() {
        return get("config.yml");
    }

    public void reloadAll() {
        files.values().forEach(YamlFile::reload);
    }
}

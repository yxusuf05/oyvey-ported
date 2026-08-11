package me.alpha432.corepvp.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A single YAML file on disk that is seeded from a jar resource the first time
 * it is needed and can be reloaded and saved afterwards.
 */
public final class YamlFile {

    private final Plugin plugin;
    private final String name;
    private final File file;
    private YamlConfiguration configuration;

    YamlFile(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
        reload();
    }

    public String name() {
        return name;
    }

    public File file() {
        return file;
    }

    public YamlConfiguration get() {
        return configuration;
    }

    public void reload() {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create directory " + parent);
            }
            if (plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            }
        }

        configuration = YamlConfiguration.loadConfiguration(file);

        // Keep files created by older versions usable: anything the shipped
        // resource knows about but the file on disk does not is added as a
        // default rather than written over the user's edits.
        InputStream defaults = plugin.getResource(name);
        if (defaults != null) {
            try (InputStreamReader reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(reader));
                configuration.options().copyDefaults(true);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not read bundled defaults for " + name + ": " + exception.getMessage());
            }
        }
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save " + name + ": " + exception.getMessage());
        }
    }
}

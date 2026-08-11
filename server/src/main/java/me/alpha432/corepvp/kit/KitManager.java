package me.alpha432.corepvp.kit;

import me.alpha432.corepvp.config.ConfigManager;
import me.alpha432.corepvp.config.YamlFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The kit registry, backed by kits.yml. */
public final class KitManager {

    private static final String FILE = "kits.yml";

    private final Plugin plugin;
    private final ConfigManager configs;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(Plugin plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void load() {
        kits.clear();
        YamlFile file = configs.file(FILE);
        ConfigurationSection root = file.get().getConfigurationSection("kits");

        if (root == null || root.getKeys(false).isEmpty()) {
            plugin.getLogger().info("No kits configured yet - installing the defaults.");
            for (Kit kit : DefaultKits.create()) {
                kits.put(kit.id(), kit);
            }
            saveAll();
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                Kit kit = KitSerializer.load(section, id.toLowerCase(Locale.ROOT));
                kits.put(kit.id(), kit);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipping kit '" + id + "': " + exception.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kit(s).");
    }

    public void saveAll() {
        YamlFile file = configs.file(FILE);
        file.get().set("kits", null);
        ConfigurationSection root = file.get().createSection("kits");
        for (Kit kit : kits.values()) {
            KitSerializer.save(root.createSection(kit.id()), kit);
        }
        file.save();
    }

    public void save(Kit kit) {
        YamlFile file = configs.file(FILE);
        ConfigurationSection root = file.get().getConfigurationSection("kits");
        if (root == null) {
            root = file.get().createSection("kits");
        }
        root.set(kit.id(), null);
        KitSerializer.save(root.createSection(kit.id()), kit);
        file.save();
    }

    public Kit byId(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return byId(id) != null;
    }

    public void register(Kit kit) {
        kits.put(kit.id(), kit);
    }

    public boolean remove(String id) {
        return kits.remove(id.toLowerCase(Locale.ROOT)) != null;
    }

    /** Enabled kits, in menu-slot order. */
    public List<Kit> enabled() {
        List<Kit> list = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (kit.enabled()) {
                list.add(kit);
            }
        }
        list.sort(Comparator.comparingInt(Kit::menuSlot));
        return list;
    }

    public List<Kit> ranked() {
        List<Kit> list = new ArrayList<>();
        for (Kit kit : enabled()) {
            if (kit.flags().rankedEnabled()) {
                list.add(kit);
            }
        }
        return list;
    }

    public List<Kit> all() {
        return List.copyOf(kits.values());
    }

    public List<String> ids() {
        return List.copyOf(kits.keySet());
    }
}

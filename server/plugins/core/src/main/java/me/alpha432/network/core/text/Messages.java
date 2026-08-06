package me.alpha432.network.core.text;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Per plugin {@code messages.yml}. Missing keys fall back to the copy bundled in the jar, so
 * adding a message in a new build never breaks an existing config.
 */
public final class Messages {

    private final Plugin plugin;
    private final String fileName;
    private YamlConfiguration config;
    private String prefix = "";

    public Messages(Plugin plugin) {
        this(plugin, "messages.yml");
    }

    public Messages(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists() && plugin.getResource(fileName) != null) {
            plugin.saveResource(fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource(fileName);
        if (bundled != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        prefix = config.getString("prefix", "");
    }

    public String raw(String key) {
        String value = config.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return "<red>Missing message: " + key;
        }
        return value.replace("<prefix>", prefix);
    }

    public Component get(String key, Object... placeholders) {
        return Msg.mm(raw(key), placeholders);
    }

    public List<Component> getList(String key, Object... placeholders) {
        List<String> raw = config.getStringList(key);
        List<Component> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(Msg.mm(line.replace("<prefix>", prefix), placeholders));
        }
        return lines;
    }

    public List<String> rawList(String key) {
        return config.getStringList(key);
    }

    public void send(CommandSender target, String key, Object... placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        target.sendMessage(Msg.mm(raw, placeholders));
    }

    public void actionBar(Player target, String key, Object... placeholders) {
        target.sendActionBar(get(key, placeholders));
    }

    public YamlConfiguration config() {
        return config;
    }

    public String prefix() {
        return prefix;
    }
}

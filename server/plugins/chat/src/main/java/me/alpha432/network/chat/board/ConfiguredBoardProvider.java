package me.alpha432.network.chat.board;

import me.alpha432.network.chat.ChatPlugin;
import me.alpha432.network.core.board.BoardProvider;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.core.text.Placeholders;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default sidebars: one board definition per set of worlds, taken from {@code config.yml}.
 * Runs at priority 0 so a practice match can replace it while a fight is going on.
 */
public final class ConfiguredBoardProvider implements BoardProvider {

    private final ChatPlugin plugin;
    private final Map<String, BoardDefinition> byWorld = new LinkedHashMap<>();

    public ConfiguredBoardProvider(ChatPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        byWorld.clear();
        ConfigurationSection boards = plugin.getConfig().getConfigurationSection("boards");
        if (boards == null) {
            return;
        }
        for (String key : boards.getKeys(false)) {
            ConfigurationSection section = boards.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            BoardDefinition definition = new BoardDefinition(
                    section.getString("title", "<white>Server"),
                    section.getStringList("lines"));
            for (String world : section.getStringList("worlds")) {
                byWorld.put(world.toLowerCase(), definition);
            }
        }
    }

    @Override
    public boolean appliesTo(Player player) {
        return byWorld.containsKey(player.getWorld().getName().toLowerCase());
    }

    @Override
    public Component title(Player player) {
        BoardDefinition definition = definition(player);
        return definition == null ? Component.empty() : Msg.mm(definition.title());
    }

    @Override
    public List<Component> lines(Player player) {
        BoardDefinition definition = definition(player);
        if (definition == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(definition.lines().size());
        for (String line : definition.lines()) {
            lines.add(Msg.mm(Placeholders.apply(player, line)));
        }
        return lines;
    }

    private BoardDefinition definition(Player player) {
        return byWorld.get(player.getWorld().getName().toLowerCase());
    }

    private record BoardDefinition(String title, List<String> lines) {
    }
}

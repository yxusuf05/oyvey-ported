package me.alpha432.network.lobby.menu;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.Menu;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.core.text.Placeholders;
import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** The compass menu: pick SMP, practice or any other configured destination. */
public final class SelectorMenu extends Menu {

    private final LobbyPlugin plugin;
    private final Player viewer;

    public SelectorMenu(LobbyPlugin plugin, Player viewer) {
        super(Msg.mm(plugin.getConfig().getString("selector.title", "<dark_gray>Auswahl")),
                plugin.getConfig().getInt("selector.rows", 3));
        this.plugin = plugin;
        this.viewer = viewer;
        build();
    }

    private void build() {
        ConfigurationSection entries = plugin.getConfig().getConfigurationSection("selector.entries");
        if (entries == null) {
            return;
        }
        for (String key : entries.getKeys(false)) {
            ConfigurationSection entry = entries.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Material material = Material.matchMaterial(entry.getString("material", "PAPER"));
            if (material == null) {
                plugin.getLogger().warning("Unknown material for selector entry " + key);
                continue;
            }
            List<String> lore = new ArrayList<>(entry.getStringList("lore"));
            String world = entry.getString("world");
            if (world != null) {
                lore.add("");
                lore.add(plugin.messages().raw("selector.player-count")
                        .replace("<count>", String.valueOf(playersIn(world))));
            }
            set(entry.getInt("slot", 0),
                    ItemBuilder.of(material)
                            .name(Placeholders.apply(viewer, entry.getString("name", key)))
                            .lore(lore)
                            .hideAttributes()
                            .build(),
                    event -> activate(entry));
        }
        fill(filler());
    }

    private void activate(ConfigurationSection entry) {
        viewer.closeInventory();
        String permission = entry.getString("permission");
        if (permission != null && !viewer.hasPermission(permission)) {
            plugin.messages().send(viewer, "selector.no-permission");
            Sounds.play(viewer, plugin.getConfig().getString("selector.deny-sound", ""), 1f, 1f);
            return;
        }

        String command = entry.getString("command");
        if (command != null && !command.isBlank()) {
            viewer.performCommand(command);
            return;
        }

        String worldName = entry.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.messages().send(viewer, "selector.world-missing", "<world>", String.valueOf(worldName));
            return;
        }
        Sounds.play(viewer, plugin.getConfig().getString("selector.select-sound", ""), 1f, 1f);
        plugin.messages().send(viewer, "selector.sending",
                "<server>", Msg.plain(Msg.mm(entry.getString("name", worldName))));
        Core.teleports().teleport(viewer, world.getSpawnLocation(), false);
    }

    private static int playersIn(String worldName) {
        World world = Bukkit.getWorld(worldName);
        return world == null ? 0 : world.getPlayers().size();
    }
}

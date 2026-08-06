package me.alpha432.network.lobby;

import me.alpha432.network.core.menu.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * The hotbar items every player carries in the lobby. Each item is tagged in its persistent
 * data so the interact listener knows what it does without comparing display names.
 */
public final class JoinItems {

    private final LobbyPlugin plugin;
    private final NamespacedKey actionKey;

    public JoinItems(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    public void give(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("join-items.items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) {
                continue;
            }
            Material material = Material.matchMaterial(item.getString("material", "COMPASS"));
            if (material == null) {
                plugin.getLogger().warning("Unknown material for join item " + key);
                continue;
            }
            ItemStack stack = ItemBuilder.of(material)
                    .name(item.getString("name", key))
                    .lore(item.getStringList("lore"))
                    .hideAttributes()
                    .build();
            tag(stack, item.getString("action", key));
            player.getInventory().setItem(item.getInt("slot", 0), stack);
        }
    }

    /** Swaps the visibility item so its colour matches the current state. */
    public void updateVisibilityItem(Player player, boolean visible) {
        ConfigurationSection item = plugin.getConfig()
                .getConfigurationSection("join-items.items.visibility");
        if (item == null) {
            return;
        }
        Material material = Material.matchMaterial(
                visible ? item.getString("material", "LIME_DYE") : item.getString("material-hidden", "GRAY_DYE"));
        if (material == null) {
            return;
        }
        ItemStack stack = ItemBuilder.of(material)
                .name(visible ? item.getString("name", "") : item.getString("name-hidden", ""))
                .lore(item.getStringList("lore"))
                .hideAttributes()
                .build();
        tag(stack, "visibility");
        player.getInventory().setItem(item.getInt("slot", 4), stack);
    }

    public String actionOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void tag(ItemStack stack, String action) {
        stack.editMeta(meta -> meta.getPersistentDataContainer()
                .set(actionKey, PersistentDataType.STRING, action.toLowerCase(Locale.ROOT)));
    }
}

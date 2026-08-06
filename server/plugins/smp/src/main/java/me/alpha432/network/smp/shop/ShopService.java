package me.alpha432.network.smp.shop;

import me.alpha432.network.core.rank.Permissions;
import me.alpha432.network.core.util.ItemParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads {@code shop.yml}: categories, their entries and the buy/sell prices. */
public final class ShopService {

    /** Rank perk: every 1 of this tier adds one percent to the sell price. */
    public static final String SELL_BONUS_PERMISSION = "network.smp.sellbonus.";

    public record Entry(Material material, ItemStack display, double buyPrice, double sellPrice, int amount) {
        public boolean canBuy() {
            return buyPrice > 0;
        }

        public boolean canSell() {
            return sellPrice > 0;
        }
    }

    public record Category(String id, String displayName, Material icon, int slot, List<Entry> entries) {
    }

    private final Plugin plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, Entry> byMaterial = new LinkedHashMap<>();

    public ShopService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        categories.clear();
        byMaterial.clear();

        ConfigurationSection root = config.getConfigurationSection("categories");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Entry> entries = new ArrayList<>();
            ConfigurationSection items = section.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection item = items.getConfigurationSection(key);
                    if (item == null) {
                        continue;
                    }
                    Material material = Material.matchMaterial(item.getString("material", key));
                    if (material == null) {
                        plugin.getLogger().warning("Unknown material in shop.yml: " + key);
                        continue;
                    }
                    ItemStack display = ItemParser.parse(item.get("display"));
                    Entry entry = new Entry(material,
                            display == null ? new ItemStack(material) : display,
                            item.getDouble("buy", 0.0D),
                            item.getDouble("sell", 0.0D),
                            Math.max(1, item.getInt("amount", 1)));
                    entries.add(entry);
                    byMaterial.putIfAbsent(material, entry);
                }
            }
            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            categories.put(id.toLowerCase(Locale.ROOT), new Category(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    icon == null ? Material.CHEST : icon,
                    section.getInt("slot", categories.size()),
                    entries));
        }
    }

    public List<Category> categories() {
        List<Category> list = new ArrayList<>(categories.values());
        list.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        return list;
    }

    public Entry entry(Material material) {
        return byMaterial.get(material);
    }

    /** Sell price for one item including the player's rank bonus. */
    public double sellPrice(Player player, Entry entry) {
        double bonus = Permissions.highestPercent(player, SELL_BONUS_PERMISSION);
        return entry.sellPrice() * (1.0D + bonus);
    }
}

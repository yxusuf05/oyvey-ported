package me.alpha432.network.practice.kit;

import me.alpha432.network.core.rank.Permissions;
import me.alpha432.network.core.storage.Database;
import me.alpha432.network.core.util.ItemParser;
import me.alpha432.network.core.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Practice kits and the per player layouts on top of them. A layout only replaces the inventory
 * contents; armour, effects and the rules always come from the configured kit.
 */
public final class KitService {

    /** Rank perk: how many kits a player may re-arrange. */
    public static final String CUSTOM_KITS_PERMISSION = "network.practice.customkits.";

    private final Plugin plugin;
    private final Database database;
    private final Map<String, PracticeKit> kits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ItemStack[]>> layouts = new ConcurrentHashMap<>();

    public KitService(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        kits.clear();
        ConfigurationSection root = config.getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Material icon = Material.matchMaterial(section.getString("icon", "IRON_SWORD"));
            ConfigurationSection armor = section.getConfigurationSection("armor");
            List<ItemStack> contents = ItemParser.parseList(section.getList("items"));
            List<PotionEffect> effects = ItemParser.potionEffects(section.getStringList("effects"));
            kits.put(id.toLowerCase(Locale.ROOT), new PracticeKit(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    icon == null ? Material.IRON_SWORD : icon,
                    section.getInt("slot", kits.size()),
                    section.getBoolean("ranked", true),
                    section.getBoolean("build", false),
                    section.getBoolean("hunger", false),
                    section.getInt("damage-ticks", 20),
                    section.getBoolean("boxing", false),
                    section.getInt("hits-to-win", 100),
                    section.getBoolean("sumo", false),
                    section.getInt("void-level", Integer.MIN_VALUE),
                    contents,
                    armor == null ? null : ItemParser.parse(armor.get("helmet")),
                    armor == null ? null : ItemParser.parse(armor.get("chestplate")),
                    armor == null ? null : ItemParser.parse(armor.get("leggings")),
                    armor == null ? null : ItemParser.parse(armor.get("boots")),
                    effects));
        }
    }

    public Optional<PracticeKit> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<PracticeKit> all() {
        return kits.values();
    }

    public List<PracticeKit> ranked() {
        List<PracticeKit> result = new ArrayList<>();
        for (PracticeKit kit : kits.values()) {
            if (kit.ranked()) {
                result.add(kit);
            }
        }
        return result;
    }

    public List<String> names() {
        return new ArrayList<>(kits.keySet());
    }

    public int customKitLimit(Player player) {
        return Permissions.highest(player, CUSTOM_KITS_PERMISSION, 0);
    }

    /** The player's own layout for this kit, or the configured default. */
    public ItemStack[] contentsFor(Player player, PracticeKit kit) {
        ItemStack[] layout = layouts.getOrDefault(player.getUniqueId(), Map.of()).get(kit.id());
        return layout == null ? kit.storageContents() : Items.copy(layout);
    }

    public boolean hasLayout(Player player, PracticeKit kit) {
        return layouts.getOrDefault(player.getUniqueId(), Map.of()).containsKey(kit.id());
    }

    public int layoutCount(Player player) {
        return layouts.getOrDefault(player.getUniqueId(), Map.of()).size();
    }

    /** @return false when the player already used up their custom kit slots. */
    public boolean saveLayout(Player player, PracticeKit kit, ItemStack[] contents) {
        Map<String, ItemStack[]> own = layouts.computeIfAbsent(player.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        if (!own.containsKey(kit.id()) && own.size() >= customKitLimit(player)) {
            return false;
        }
        own.put(kit.id(), Items.copy(contents));
        database.executeAsync(database.upsert("practice_kit_layouts",
                        "uuid, kit, contents", "?, ?, ?", "uuid, kit", "contents = ?"),
                player.getUniqueId().toString(), kit.id(), Items.serialize(contents),
                Items.serialize(contents));
        return true;
    }

    public void resetLayout(Player player, PracticeKit kit) {
        Map<String, ItemStack[]> own = layouts.get(player.getUniqueId());
        if (own != null) {
            own.remove(kit.id());
        }
        database.executeAsync("DELETE FROM practice_kit_layouts WHERE uuid = ? AND kit = ?",
                player.getUniqueId().toString(), kit.id());
    }

    public void load(UUID uuid) {
        Map<String, ItemStack[]> own = new ConcurrentHashMap<>();
        for (String[] row : database.queryList(
                "SELECT kit, contents FROM practice_kit_layouts WHERE uuid = ?",
                results -> new String[]{results.getString("kit"), results.getString("contents")},
                uuid.toString())) {
            try {
                own.put(row[0], Items.deserialize(row[1]));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not read the " + row[0] + " layout of " + uuid);
            }
        }
        layouts.put(uuid, own);
    }

    public void unload(UUID uuid) {
        layouts.remove(uuid);
    }
}

package me.alpha432.network.smp.kit;

import me.alpha432.network.core.storage.Database;
import me.alpha432.network.core.util.ItemParser;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

/** Loads {@code kits.yml} and tracks per player cooldowns in the database. */
public final class KitService {

    private final Plugin plugin;
    private final Database database;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

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
            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            ConfigurationSection armor = section.getConfigurationSection("armor");
            kits.put(id.toLowerCase(Locale.ROOT), new Kit(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    icon == null ? Material.CHEST : icon,
                    section.getString("permission"),
                    section.getLong("cooldown-seconds", 0L),
                    section.getBoolean("first-join", false),
                    section.getDouble("price", 0.0D),
                    ItemParser.parseList(section.getList("items")),
                    armor == null ? null : ItemParser.parse(armor.get("helmet")),
                    armor == null ? null : ItemParser.parse(armor.get("chestplate")),
                    armor == null ? null : ItemParser.parse(armor.get("leggings")),
                    armor == null ? null : ItemParser.parse(armor.get("boots")),
                    ItemParser.parse(section.get("off-hand")),
                    ItemParser.potionEffects(section.getStringList("effects"))));
        }
    }

    public Optional<Kit> get(String id) {
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public List<Kit> availableTo(CommandSender sender) {
        List<Kit> available = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (kit.permission() == null || sender.hasPermission(kit.permission())) {
                available.add(kit);
            }
        }
        return available;
    }

    public List<String> names() {
        return new ArrayList<>(kits.keySet());
    }

    /** Remaining cooldown in seconds, 0 when the kit is ready. */
    public long remaining(Player player, Kit kit) {
        Long expires = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(kit.id());
        if (expires == null) {
            return 0L;
        }
        if (kit.isOneTime()) {
            // A one time kit stores Long.MAX_VALUE, which never runs out.
            return expires == Long.MAX_VALUE ? Long.MAX_VALUE : 0L;
        }
        long left = expires - System.currentTimeMillis();
        return left <= 0 ? 0L : (left + 999L) / 1000L;
    }

    public boolean isReady(Player player, Kit kit) {
        return remaining(player, kit) == 0L;
    }

    /** Puts the kit into the player's inventory; leftovers drop at their feet. */
    public void give(Player player, Kit kit) {
        for (ItemStack item : kit.items()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        if (kit.helmet() != null) {
            player.getInventory().setHelmet(kit.helmet().clone());
        }
        if (kit.chestplate() != null) {
            player.getInventory().setChestplate(kit.chestplate().clone());
        }
        if (kit.leggings() != null) {
            player.getInventory().setLeggings(kit.leggings().clone());
        }
        if (kit.boots() != null) {
            player.getInventory().setBoots(kit.boots().clone());
        }
        if (kit.offHand() != null) {
            player.getInventory().setItemInOffHand(kit.offHand().clone());
        }
        kit.effects().forEach(player::addPotionEffect);
        markUsed(player, kit);
    }

    public void markUsed(Player player, Kit kit) {
        long expires = kit.isOneTime()
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + kit.cooldownSeconds() * 1000L;
        cooldowns.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>())
                .put(kit.id(), expires);
        database.executeAsync(database.upsert("smp_kit_cooldowns",
                        "uuid, kit, expires", "?, ?, ?", "uuid, kit", "expires = ?"),
                player.getUniqueId().toString(), kit.id(), expires, expires);
    }

    /** Called from the pre-login handler so the data is ready when the player arrives. */
    public void load(UUID uuid) {
        Map<String, Long> loaded = new ConcurrentHashMap<>();
        for (Object[] row : database.queryList(
                "SELECT kit, expires FROM smp_kit_cooldowns WHERE uuid = ?",
                results -> new Object[]{results.getString("kit"), results.getLong("expires")},
                uuid.toString())) {
            loaded.put((String) row[0], (Long) row[1]);
        }
        cooldowns.put(uuid, loaded);
    }

    public void unload(UUID uuid) {
        cooldowns.remove(uuid);
    }

    /** True when the player never claimed the first-join kit. */
    public boolean hasClaimed(UUID uuid, Kit kit) {
        return cooldowns.getOrDefault(uuid, Map.of()).containsKey(kit.id());
    }
}

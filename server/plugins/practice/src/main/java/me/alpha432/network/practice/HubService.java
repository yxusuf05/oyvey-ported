package me.alpha432.network.practice;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

/** The practice hub: where players stand when they are neither fighting nor queued. */
public final class HubService {

    private final PracticePlugin plugin;
    private final File file;
    private final NamespacedKey actionKey;
    private Location spawn;

    public HubService(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hub.yml");
        this.actionKey = new NamespacedKey(plugin, "action");
        load();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        spawn = Locations.deserialize(YamlConfiguration.loadConfiguration(file).getString("spawn"));
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawn", Locations.serialize(spawn));
        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save hub.yml", e);
        }
    }

    public String worldName() {
        return plugin.getConfig().getString("world", "practice");
    }

    public boolean isPractice(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName());
    }

    public boolean isPractice(Player player) {
        return isPractice(player.getWorld());
    }

    public Location spawn() {
        if (spawn != null) {
            return spawn.clone();
        }
        World world = Bukkit.getWorld(worldName());
        return world == null ? null : world.getSpawnLocation();
    }

    public void spawn(Location location) {
        this.spawn = location.clone();
        World world = location.getWorld();
        if (world != null) {
            // The lobby selector teleports to the world spawn, so keep both in sync.
            world.setSpawnLocation(location);
        }
        save();
    }

    /** Puts the player back into the hub state: teleport, clean inventory, hub items. */
    public void send(Player player) {
        Location target = spawn();
        if (target == null) {
            plugin.messages().send(player, "hub.missing-world", "<world>", worldName());
            return;
        }
        player.teleport(target);
        reset(player);
    }

    /** Hub state without the teleport, used when a player walks into the practice world. */
    public void reset(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setLevel(0);
        player.setExp(0f);
        player.setFallDistance(0f);
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(attribute == null ? 20.0D : attribute.getValue());
        player.setAllowFlight(false);
        player.setFlying(false);
        giveItems(player);
    }

    public void giveItems(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("hub-items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) {
                continue;
            }
            Material material = Material.matchMaterial(item.getString("material", "PAPER"));
            if (material == null) {
                plugin.getLogger().warning("Unknown material for hub item " + key);
                continue;
            }
            ItemStack stack = ItemBuilder.of(material)
                    .name(item.getString("name", key))
                    .lore(item.getStringList("lore"))
                    .hideAttributes()
                    .build();
            String action = item.getString("action", key).toLowerCase(Locale.ROOT);
            stack.editMeta(meta -> meta.getPersistentDataContainer()
                    .set(actionKey, PersistentDataType.STRING, action));
            player.getInventory().setItem(item.getInt("slot", 0), stack);
        }
    }

    public String actionOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }
}

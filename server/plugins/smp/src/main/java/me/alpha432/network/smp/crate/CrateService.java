package me.alpha432.network.smp.crate;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.util.ItemParser;
import me.alpha432.network.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Loads {@code crates.yml}, builds and validates key items and rolls rewards.
 *
 * <p>Keys are ordinary items carrying the crate id in their persistent data, so they survive
 * renaming and cannot be faked with an anvil.
 */
public final class CrateService {

    private final Plugin plugin;
    private final File file;
    private final NamespacedKey keyTag;
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    public CrateService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        this.keyTag = new NamespacedKey(plugin, "crate_key");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("crates.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        crates.clear();
        ConfigurationSection root = config.getConfigurationSection("crates");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String crateId = id.toLowerCase(Locale.ROOT);
            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            crates.put(crateId, new Crate(
                    crateId,
                    section.getString("display-name", id),
                    icon == null ? Material.CHEST : icon,
                    buildKeyTemplate(crateId, section.getConfigurationSection("key")),
                    readRewards(crateId, section.getConfigurationSection("rewards")),
                    Locations.deserialize(section.getString("block"))));
        }
    }

    private ItemStack buildKeyTemplate(String crateId, ConfigurationSection section) {
        Material material = Material.matchMaterial(
                section == null ? "TRIPWIRE_HOOK" : section.getString("material", "TRIPWIRE_HOOK"));
        ItemBuilder builder = ItemBuilder.of(material == null ? Material.TRIPWIRE_HOOK : material)
                .name(section == null ? "<yellow>Key" : section.getString("name", "<yellow>Key"))
                .lore(section == null ? List.of() : section.getStringList("lore"))
                .hideAttributes();
        if (section == null || section.getBoolean("glow", true)) {
            builder.glow();
        }
        ItemStack key = builder.build();
        key.editMeta(meta -> meta.getPersistentDataContainer()
                .set(keyTag, PersistentDataType.STRING, crateId));
        return key;
    }

    private List<CrateReward> readRewards(String crateId, ConfigurationSection section) {
        List<CrateReward> rewards = new ArrayList<>();
        if (section == null) {
            return rewards;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection reward = section.getConfigurationSection(id);
            if (reward == null) {
                continue;
            }
            List<ItemStack> items = ItemParser.parseList(reward.getList("items"));
            ItemStack display = ItemParser.parse(reward.get("display"));
            if (display == null) {
                display = items.isEmpty() ? new ItemStack(Material.PAPER) : items.get(0).clone();
            }
            double weight = reward.getDouble("weight", 1.0D);
            if (weight <= 0) {
                plugin.getLogger().warning("Reward " + id + " of crate " + crateId
                        + " has a weight of 0 and can never be won.");
            }
            rewards.add(new CrateReward(
                    id.toLowerCase(Locale.ROOT),
                    reward.getString("display-name", id),
                    display,
                    items,
                    reward.getStringList("commands"),
                    weight,
                    reward.getBoolean("broadcast", false)));
        }
        return rewards;
    }

    /** Persists only what admins change in game: the crate block positions. */
    public void saveBlocks() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (Crate crate : crates.values()) {
            config.set("crates." + crate.id() + ".block", Locations.serialize(crate.block()));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save crates.yml", e);
        }
    }

    public Optional<Crate> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(crates.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<Crate> all() {
        return crates.values();
    }

    public List<String> names() {
        return new ArrayList<>(crates.keySet());
    }

    /** The crate standing at this block, or empty. */
    public Optional<Crate> at(Location location) {
        for (Crate crate : crates.values()) {
            if (Locations.sameBlock(crate.block(), location)) {
                return Optional.of(crate);
            }
        }
        return Optional.empty();
    }

    /** A ready to give key item for the crate. */
    public ItemStack key(Crate crate, int amount) {
        ItemStack key = crate.keyTemplate().clone();
        key.setAmount(Math.max(1, Math.min(amount, key.getMaxStackSize())));
        return key;
    }

    /** The crate a key item belongs to, or {@code null} when the item is not a key. */
    public String crateOfKey(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(keyTag, PersistentDataType.STRING);
    }

    public boolean isKeyFor(ItemStack stack, Crate crate) {
        return crate.id().equals(crateOfKey(stack));
    }

    public int countKeys(Player player, Crate crate) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isKeyFor(stack, crate)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes exactly one key of this crate. Returns false when the player has none. */
    public boolean consumeKey(Player player, Crate crate) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isKeyFor(stack, crate)) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    /** Picks a reward, weighted. Returns {@code null} when the crate has no usable rewards. */
    public CrateReward roll(Crate crate) {
        double total = crate.totalWeight();
        if (total <= 0) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double seen = 0;
        for (CrateReward reward : crate.rewards()) {
            seen += reward.weight();
            if (roll < seen) {
                return reward;
            }
        }
        return crate.rewards().get(crate.rewards().size() - 1);
    }

    /** Hands the reward to the player: items into the inventory, commands through the console. */
    public void give(Player player, CrateReward reward) {
        for (ItemStack item : reward.items()) {
            player.getInventory().addItem(item.clone()).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        for (String command : reward.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%player%", player.getName()));
        }
    }
}

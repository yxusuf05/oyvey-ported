package me.alpha432.network.core.util;

import me.alpha432.network.core.menu.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads items out of configuration files. Kits, shops and starter inventories all use this, so
 * the syntax is the same everywhere.
 *
 * <p>Short form: {@code "STONE_SWORD"} or {@code "BREAD:16"}.
 * <p>Long form: a map with {@code material}, {@code amount}, {@code name}, {@code lore},
 * {@code enchantments}, {@code unbreakable} and {@code flags}.
 */
public final class ItemParser {

    private ItemParser() {
    }

    /** @return {@code null} when the entry cannot be read; the caller should log and skip. */
    public static ItemStack parse(Object raw) {
        if (raw instanceof String text) {
            return fromShortForm(text);
        }
        if (raw instanceof ConfigurationSection section) {
            return fromSection(section);
        }
        if (raw instanceof Map<?, ?> map) {
            MemoryConfiguration wrapper = new MemoryConfiguration();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                wrapper.set(String.valueOf(entry.getKey()), entry.getValue());
            }
            return fromSection(wrapper);
        }
        return null;
    }

    public static List<ItemStack> parseList(List<?> entries) {
        List<ItemStack> items = new ArrayList<>();
        if (entries == null) {
            return items;
        }
        for (Object entry : entries) {
            ItemStack stack = parse(entry);
            if (stack != null) {
                items.add(stack);
            }
        }
        return items;
    }

    private static ItemStack fromShortForm(String text) {
        String[] parts = text.split(":");
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            return null;
        }
        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        return new ItemStack(material, Math.max(1, amount));
    }

    private static ItemStack fromSection(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null) {
            return null;
        }
        ItemBuilder builder = ItemBuilder.of(material, section.getInt("amount", 1));
        if (section.contains("name")) {
            builder.name(section.getString("name"));
        }
        if (section.contains("lore")) {
            builder.lore(section.getStringList("lore"));
        }
        if (section.getBoolean("unbreakable", false)) {
            builder.unbreakable();
        }
        if (section.getBoolean("hide-attributes", true)) {
            builder.hideAttributes();
        }
        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                Enchantment enchantment = enchantment(key);
                if (enchantment == null) {
                    continue;
                }
                builder.enchant(enchantment, Math.max(1, enchantments.getInt(key, 1)));
            }
        }
        return builder.build();
    }

    public static Enchantment enchantment(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    /** Parses {@code SPEED:1:600} into a potion effect; the duration is in seconds. */
    public static PotionEffect potionEffect(String raw) {
        String[] parts = raw.split(":");
        NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
        PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
        if (type == null) {
            return null;
        }
        int amplifier = parts.length > 1 ? Math.max(1, parseInt(parts[1], 1)) - 1 : 0;
        int seconds = parts.length > 2 ? parseInt(parts[2], 600) : 600;
        return new PotionEffect(type, seconds * 20, amplifier, false, false, true);
    }

    public static List<PotionEffect> potionEffects(List<String> raw) {
        List<PotionEffect> effects = new ArrayList<>();
        if (raw == null) {
            return effects;
        }
        for (String entry : raw) {
            PotionEffect effect = potionEffect(entry);
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

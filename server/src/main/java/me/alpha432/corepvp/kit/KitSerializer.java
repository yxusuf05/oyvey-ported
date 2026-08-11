package me.alpha432.corepvp.kit;

import me.alpha432.corepvp.combat.CombatMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Kit to YAML and back.
 *
 * <p>Items go through {@link ItemStack#serializeItemsAsBytes(ItemStack[])} and
 * Base64 rather than being described field by field. That is the only form that
 * survives enchantments, custom names and data components intact, and Paper
 * migrates it across Minecraft versions for us.
 */
public final class KitSerializer {

    private KitSerializer() {
    }

    public static void save(ConfigurationSection section, Kit kit) {
        section.set("display", kit.displayName());
        section.set("icon", encode(new ItemStack[]{kit.icon()}));
        section.set("slot", kit.menuSlot());
        section.set("enabled", kit.enabled());
        section.set("combat-mode", kit.combatMode().name());
        section.set("arena-types", new ArrayList<>(kit.arenaTypes()));
        section.set("contents", encode(kit.contents()));
        section.set("armor", encode(kit.armor()));
        section.set("off-hand", kit.offHand() == null ? null : encode(new ItemStack[]{kit.offHand()}));

        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : kit.effects()) {
            effects.add(effect.getType().getKey() + ":" + effect.getDuration() + ":" + effect.getAmplifier());
        }
        section.set("effects", effects);

        ConfigurationSection flags = section.getConfigurationSection("flags");
        if (flags == null) {
            flags = section.createSection("flags");
        }
        kit.flags().save(flags);
    }

    public static Kit load(ConfigurationSection section, String id) {
        Kit kit = new Kit(id);
        kit.displayName(section.getString("display", id));
        kit.menuSlot(section.getInt("slot", 0));
        kit.enabled(section.getBoolean("enabled", true));
        kit.combatMode(CombatMode.parse(section.getString("combat-mode"), CombatMode.LEGACY_1_8));

        List<String> arenaTypes = section.getStringList("arena-types");
        if (!arenaTypes.isEmpty()) {
            kit.arenaTypes(new LinkedHashSet<>(arenaTypes));
        }

        ItemStack[] icon = decode(section.getString("icon"));
        if (icon.length > 0 && icon[0] != null) {
            kit.icon(icon[0]);
        } else {
            kit.icon(new ItemStack(Material.IRON_SWORD));
        }

        kit.contents(decode(section.getString("contents")));
        kit.armor(decode(section.getString("armor")));

        ItemStack[] offHand = decode(section.getString("off-hand"));
        kit.offHand(offHand.length > 0 ? offHand[0] : null);

        List<PotionEffect> effects = new ArrayList<>();
        for (String raw : section.getStringList("effects")) {
            PotionEffect effect = parseEffect(raw);
            if (effect != null) {
                effects.add(effect);
            }
        }
        kit.effects(effects);
        kit.flags(KitFlags.load(section.getConfigurationSection("flags")));
        return kit;
    }

    public static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    public static ItemStack[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[0];
        }
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException exception) {
            // Corrupt or hand-edited data: better an empty kit than a broken
            // start-up. The caller logs it.
            return new ItemStack[0];
        }
    }

    private static PotionEffect parseEffect(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 3) {
            return null;
        }
        // The key itself contains a colon, so the last two parts are the numbers.
        String key = String.join(":", List.of(parts).subList(0, parts.length - 2));
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        if (namespaced == null) {
            return null;
        }
        PotionEffectType type = Registry.EFFECT.get(namespaced);
        if (type == null) {
            return null;
        }
        try {
            int duration = Integer.parseInt(parts[parts.length - 2]);
            int amplifier = Integer.parseInt(parts[parts.length - 1]);
            return new PotionEffect(type, duration, amplifier, false, false);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

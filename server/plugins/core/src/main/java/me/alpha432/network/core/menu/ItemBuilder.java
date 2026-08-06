package me.alpha432.network.core.menu;

import me.alpha432.network.core.text.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fluent {@link ItemStack} construction. Names and lore accept MiniMessage. */
public final class ItemBuilder {

    private final ItemStack stack;

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(new ItemStack(material, amount));
    }

    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return this;
    }

    public ItemBuilder name(String miniMessage, Object... placeholders) {
        return meta(meta -> meta.displayName(Msg.item(miniMessage, placeholders)));
    }

    public ItemBuilder name(Component component) {
        return meta(meta -> meta.displayName(component.decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(Msg.item(line));
        }
        return meta(meta -> meta.lore(components));
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (Component line : lines) {
            components.add(line.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        return meta(meta -> meta.lore(components));
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        stack.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    /** Adds the enchantment shimmer without an actual enchantment. */
    public ItemBuilder glow() {
        return meta(meta -> meta.setEnchantmentGlintOverride(true));
    }

    public ItemBuilder flags(ItemFlag... flags) {
        return meta(meta -> meta.addItemFlags(flags));
    }

    public ItemBuilder hideAttributes() {
        return flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    public ItemBuilder unbreakable() {
        return meta(meta -> meta.setUnbreakable(true));
    }

    public ItemBuilder skull(OfflinePlayer owner) {
        return meta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(owner);
            }
        });
    }

    public ItemBuilder leatherColor(org.bukkit.Color color) {
        return meta(meta -> {
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(color);
            }
        });
    }

    public ItemBuilder potionEffect(PotionEffect effect) {
        return meta(meta -> {
            if (meta instanceof PotionMeta potion) {
                potion.addCustomEffect(effect, true);
            }
        });
    }

    public ItemBuilder potionType(org.bukkit.potion.PotionType type) {
        return meta(meta -> {
            if (meta instanceof PotionMeta potion) {
                potion.setBasePotionType(type);
            }
        });
    }

    public ItemStack build() {
        return stack;
    }

    private ItemBuilder meta(java.util.function.Consumer<ItemMeta> action) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            action.accept(meta);
            stack.setItemMeta(meta);
        }
        return this;
    }
}

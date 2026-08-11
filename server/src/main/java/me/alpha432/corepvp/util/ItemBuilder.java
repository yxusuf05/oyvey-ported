package me.alpha432.corepvp.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Fluent {@link ItemStack} construction for GUI icons and kit contents. */
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

    public static ItemBuilder copyOf(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    /** Sets the display name from MiniMessage input, without the default italics. */
    public ItemBuilder name(String miniMessage) {
        return meta(meta -> meta.displayName(Text.item(miniMessage)));
    }

    public ItemBuilder name(Component component) {
        return meta(meta -> meta.displayName(Text.item(component)));
    }

    public ItemBuilder lore(String... miniMessageLines) {
        return lore(Arrays.asList(miniMessageLines));
    }

    public ItemBuilder lore(List<String> miniMessageLines) {
        List<Component> lines = new ArrayList<>(miniMessageLines.size());
        for (String line : miniMessageLines) {
            lines.add(Text.item(line));
        }
        return loreComponents(lines);
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        List<Component> cleaned = new ArrayList<>(lines.size());
        for (Component line : lines) {
            cleaned.add(Text.item(line));
        }
        return meta(meta -> meta.lore(cleaned));
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        stack.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        return meta(meta -> meta.addItemFlags(flags));
    }

    /** Adds the enchantment shimmer without an actual enchantment. */
    public ItemBuilder glow() {
        return meta(meta -> meta.setEnchantmentGlintOverride(true));
    }

    public ItemBuilder unbreakable() {
        return meta(meta -> meta.setUnbreakable(true));
    }

    public ItemBuilder meta(Consumer<ItemMeta> consumer) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            stack.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return stack;
    }
}

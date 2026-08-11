package me.alpha432.corepvp.kit;

import me.alpha432.corepvp.combat.CombatMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The kits a fresh server starts with.
 *
 * <p>Written in code rather than shipped as a YAML blob so the loadouts are
 * readable and reviewable. They are saved to kits.yml on first start and are
 * an admin's to edit from then on.
 */
public final class DefaultKits {

    private DefaultKits() {
    }

    public static List<Kit> create() {
        List<Kit> kits = new ArrayList<>();
        kits.add(crystal());
        kits.add(anchor());
        kits.add(sword());
        kits.add(noDebuff());
        kits.add(debuff());
        kits.add(gapple());
        kits.add(archer());
        kits.add(sumo());
        kits.add(buildUhc());
        kits.add(boxing());
        kits.add(combo());
        kits.add(vanilla());
        return kits;
    }

    // ------------------------------------------------------------------
    //  Crystal PvP
    // ------------------------------------------------------------------

    private static Kit crystal() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.NETHERITE_SWORD, 1,
                Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        contents[1] = new ItemStack(Material.END_CRYSTAL, 64);
        contents[2] = new ItemStack(Material.OBSIDIAN, 64);
        contents[3] = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16);
        contents[4] = new ItemStack(Material.TOTEM_OF_UNDYING, 4);
        contents[5] = new ItemStack(Material.ENDER_PEARL, 16);
        contents[6] = new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
        contents[7] = new ItemStack(Material.END_CRYSTAL, 64);
        contents[8] = new ItemStack(Material.OBSIDIAN, 64);
        fill(contents, 9, 17, () -> new ItemStack(Material.END_CRYSTAL, 64));
        fill(contents, 18, 26, () -> new ItemStack(Material.OBSIDIAN, 64));
        contents[27] = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32);
        contents[28] = new ItemStack(Material.TOTEM_OF_UNDYING, 8);
        contents[29] = new ItemStack(Material.EXPERIENCE_BOTTLE, 64);

        return new Kit("crystal")
                .displayName("Crystal")
                .icon(new ItemStack(Material.END_CRYSTAL))
                .menuSlot(0)
                .contents(contents)
                .armor(netheriteArmor())
                .offHand(new ItemStack(Material.TOTEM_OF_UNDYING))
                .combatMode(CombatMode.CRYSTAL)
                .arenaTypes("crystal")
                // Placing obsidian and crystals is the kit, so building has to
                // be on; the rollback journal restores the arena afterwards.
                .flags(KitFlags.defaults()
                        .withBuild(true)
                        .withHunger(true)
                        .withNaturalRegen(true));
    }

    private static Kit anchor() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.NETHERITE_SWORD, 1,
                Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        contents[1] = new ItemStack(Material.RESPAWN_ANCHOR, 32);
        contents[2] = new ItemStack(Material.GLOWSTONE, 64);
        contents[3] = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16);
        contents[4] = new ItemStack(Material.TOTEM_OF_UNDYING, 4);
        contents[5] = new ItemStack(Material.ENDER_PEARL, 16);
        contents[6] = new ItemStack(Material.OBSIDIAN, 64);
        contents[7] = new ItemStack(Material.RESPAWN_ANCHOR, 32);
        contents[8] = new ItemStack(Material.GLOWSTONE, 64);
        fill(contents, 9, 17, () -> new ItemStack(Material.GLOWSTONE, 64));
        fill(contents, 18, 23, () -> new ItemStack(Material.RESPAWN_ANCHOR, 32));

        return new Kit("anchor")
                .displayName("Anchor")
                .icon(new ItemStack(Material.RESPAWN_ANCHOR))
                .menuSlot(1)
                .contents(contents)
                .armor(netheriteArmor())
                .offHand(new ItemStack(Material.TOTEM_OF_UNDYING))
                .combatMode(CombatMode.CRYSTAL)
                .arenaTypes("crystal")
                .flags(KitFlags.defaults()
                        .withBuild(true)
                        .withHunger(true)
                        .withNaturalRegen(true));
    }

    private static Kit sword() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.NETHERITE_SWORD, 1,
                Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        contents[1] = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32);
        contents[2] = new ItemStack(Material.ENDER_PEARL, 16);

        return new Kit("sword")
                .displayName("Sword")
                .icon(new ItemStack(Material.NETHERITE_SWORD))
                .menuSlot(2)
                .contents(contents)
                .armor(netheriteArmor())
                .offHand(new ItemStack(Material.TOTEM_OF_UNDYING))
                .combatMode(CombatMode.MODERN)
                .arenaTypes("flat", "crystal")
                .flags(KitFlags.defaults().withHunger(true).withNaturalRegen(true));
    }

    // ------------------------------------------------------------------
    //  Classic 1.8 style
    // ------------------------------------------------------------------

    private static Kit noDebuff() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1,
                Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 3);
        fill(contents, 1, 8, () -> splash(PotionType.STRONG_HEALING, 1));
        fill(contents, 9, 35, () -> splash(PotionType.STRONG_HEALING, 1));

        return new Kit("nodebuff")
                .displayName("NoDebuff")
                .icon(splash(PotionType.STRONG_HEALING, 1))
                .menuSlot(3)
                .contents(contents)
                .armor(diamondArmor(2))
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("flat")
                .flags(KitFlags.defaults());
    }

    private static Kit debuff() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1,
                Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 3);
        contents[1] = splash(PotionType.STRONG_POISON, 1);
        contents[2] = splash(PotionType.STRONG_HARMING, 1);
        contents[3] = splash(PotionType.SLOWNESS, 1);
        contents[4] = splash(PotionType.WEAKNESS, 1);
        contents[5] = new ItemStack(Material.MILK_BUCKET);
        fill(contents, 6, 8, () -> splash(PotionType.STRONG_HEALING, 1));
        fill(contents, 9, 30, () -> splash(PotionType.STRONG_HEALING, 1));
        fill(contents, 31, 35, () -> splash(PotionType.STRONG_POISON, 1));

        return new Kit("debuff")
                .displayName("Debuff")
                .icon(splash(PotionType.STRONG_POISON, 1))
                .menuSlot(4)
                .contents(contents)
                .armor(diamondArmor(2))
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("flat")
                .flags(KitFlags.defaults());
    }

    private static Kit gapple() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1,
                Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        fill(contents, 1, 8, () -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64));
        fill(contents, 9, 17, () -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 64));

        return new Kit("gapple")
                .displayName("Gapple")
                .icon(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE))
                .menuSlot(5)
                .contents(contents)
                .armor(diamondArmor(4))
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("flat")
                .flags(KitFlags.defaults().withHunger(true).withNaturalRegen(true));
    }

    private static Kit archer() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1, Enchantment.SHARPNESS, 1);
        contents[1] = enchanted(Material.BOW, 1,
                Enchantment.POWER, 3, Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3);
        fill(contents, 2, 8, () -> splash(PotionType.STRONG_HEALING, 1));
        contents[9] = new ItemStack(Material.ARROW, 1);
        fill(contents, 10, 26, () -> splash(PotionType.STRONG_HEALING, 1));

        return new Kit("archer")
                .displayName("Archer")
                .icon(new ItemStack(Material.BOW))
                .menuSlot(6)
                .contents(contents)
                .armor(chainmailArmor())
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("flat")
                .flags(KitFlags.defaults());
    }

    private static Kit sumo() {
        return new Kit("sumo")
                .displayName("Sumo")
                .icon(new ItemStack(Material.LEATHER_BOOTS))
                .menuSlot(7)
                .contents(new ItemStack[Kit.CONTENT_SIZE])
                .armor(new ItemStack[Kit.ARMOR_SIZE])
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("sumo")
                // No damage at all: the loser is whoever leaves the platform.
                .flags(KitFlags.defaults().withSumo(true).withEditable(false));
    }

    private static Kit buildUhc() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1,
                Enchantment.SHARPNESS, 2, Enchantment.UNBREAKING, 3);
        contents[1] = enchanted(Material.DIAMOND_PICKAXE, 1, Enchantment.EFFICIENCY, 2);
        contents[2] = new ItemStack(Material.GOLDEN_APPLE, 8);
        contents[3] = new ItemStack(Material.COBBLESTONE, 64);
        contents[4] = new ItemStack(Material.COBBLESTONE, 64);
        contents[5] = new ItemStack(Material.WATER_BUCKET);
        contents[8] = new ItemStack(Material.OAK_PLANKS, 64);

        return new Kit("builduhc")
                .displayName("BuildUHC")
                .icon(new ItemStack(Material.COBBLESTONE))
                .menuSlot(8)
                .contents(contents)
                .armor(diamondArmor(2))
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("build")
                .flags(KitFlags.defaults()
                        .withBuild(true)
                        .withHunger(true)
                        .withNaturalRegen(true));
    }

    private static Kit boxing() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1, Enchantment.UNBREAKING, 3);

        return new Kit("boxing")
                .displayName("Boxing")
                .icon(new ItemStack(Material.LEATHER_CHESTPLATE))
                .menuSlot(9)
                .contents(contents)
                .armor(diamondArmor(0))
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("flat")
                // Damage never kills; the first player to land 100 hits wins.
                .flags(KitFlags.defaults().withBoxing(true).withEditable(false));
    }

    private static Kit combo() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.DIAMOND_SWORD, 1, Enchantment.SHARPNESS, 1);
        fill(contents, 1, 8, () -> splash(PotionType.STRONG_HEALING, 1));

        return new Kit("combo")
                .displayName("Combo")
                .icon(new ItemStack(Material.COOKED_BEEF))
                .menuSlot(10)
                .contents(contents)
                .armor(leatherArmor())
                .combatMode(CombatMode.LEGACY_1_8)
                .arenaTypes("flat")
                .flags(KitFlags.defaults());
    }

    private static Kit vanilla() {
        ItemStack[] contents = new ItemStack[Kit.CONTENT_SIZE];
        contents[0] = enchanted(Material.IRON_SWORD, 1, Enchantment.SHARPNESS, 2);
        contents[1] = new ItemStack(Material.GOLDEN_APPLE, 16);
        contents[2] = new ItemStack(Material.COOKED_BEEF, 32);
        contents[8] = new ItemStack(Material.BOW);
        contents[9] = new ItemStack(Material.ARROW, 32);

        return new Kit("vanilla")
                .displayName("Vanilla")
                .icon(new ItemStack(Material.IRON_SWORD))
                .menuSlot(11)
                .contents(contents)
                .armor(ironArmor())
                .offHand(new ItemStack(Material.SHIELD))
                .combatMode(CombatMode.MODERN)
                .arenaTypes("flat")
                .flags(KitFlags.defaults().withHunger(true).withNaturalRegen(true));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static ItemStack[] netheriteArmor() {
        return new ItemStack[]{
                enchanted(Material.NETHERITE_BOOTS, 1, Enchantment.PROTECTION, 4,
                        Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4),
                enchanted(Material.NETHERITE_LEGGINGS, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                enchanted(Material.NETHERITE_CHESTPLATE, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
                enchanted(Material.NETHERITE_HELMET, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)};
    }

    private static ItemStack[] diamondArmor(int protection) {
        return new ItemStack[]{
                armorPiece(Material.DIAMOND_BOOTS, protection),
                armorPiece(Material.DIAMOND_LEGGINGS, protection),
                armorPiece(Material.DIAMOND_CHESTPLATE, protection),
                armorPiece(Material.DIAMOND_HELMET, protection)};
    }

    private static ItemStack[] ironArmor() {
        return new ItemStack[]{
                armorPiece(Material.IRON_BOOTS, 0),
                armorPiece(Material.IRON_LEGGINGS, 0),
                armorPiece(Material.IRON_CHESTPLATE, 0),
                armorPiece(Material.IRON_HELMET, 0)};
    }

    private static ItemStack[] chainmailArmor() {
        return new ItemStack[]{
                armorPiece(Material.CHAINMAIL_BOOTS, 2),
                armorPiece(Material.CHAINMAIL_LEGGINGS, 2),
                armorPiece(Material.CHAINMAIL_CHESTPLATE, 2),
                armorPiece(Material.CHAINMAIL_HELMET, 2)};
    }

    private static ItemStack[] leatherArmor() {
        return new ItemStack[]{
                armorPiece(Material.LEATHER_BOOTS, 1),
                armorPiece(Material.LEATHER_LEGGINGS, 1),
                armorPiece(Material.LEATHER_CHESTPLATE, 1),
                armorPiece(Material.LEATHER_HELMET, 1)};
    }

    private static ItemStack armorPiece(Material material, int protection) {
        return protection > 0
                ? enchanted(material, 1, Enchantment.PROTECTION, protection, Enchantment.UNBREAKING, 3)
                : enchanted(material, 1, Enchantment.UNBREAKING, 3);
    }

    /** {@code enchanted(material, amount, enchantment, level, enchantment, level, ...)} */
    private static ItemStack enchanted(Material material, int amount, Object... enchantments) {
        ItemStack stack = new ItemStack(material, amount);
        for (int i = 0; i + 1 < enchantments.length; i += 2) {
            stack.addUnsafeEnchantment((Enchantment) enchantments[i], (Integer) enchantments[i + 1]);
        }
        return stack;
    }

    private static ItemStack splash(PotionType type, int amount) {
        ItemStack stack = new ItemStack(Material.SPLASH_POTION, amount);
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(type);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void fill(ItemStack[] contents, int from, int to, java.util.function.Supplier<ItemStack> supplier) {
        for (int slot = from; slot <= to && slot < contents.length; slot++) {
            contents[slot] = supplier.get();
        }
    }
}

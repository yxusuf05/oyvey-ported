package me.alpha432.network.practice.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * A practice kit: the loadout plus the rules that make a NoDebuff fight different from Sumo.
 *
 * @param build        players may place and break blocks (bridge, build UHC)
 * @param hunger       hunger drains during the fight
 * @param damageTicks  invulnerability ticks after a hit; 0 restores 1.8 style combat
 * @param boxing       first to {@code hitsToWin} hits wins instead of first kill
 * @param sumo         losing means leaving the arena floor, no damage involved
 * @param voidLevel    below this Y a player counts as dead
 */
public record PracticeKit(String id,
                          String displayName,
                          Material icon,
                          int menuSlot,
                          boolean ranked,
                          boolean build,
                          boolean hunger,
                          int damageTicks,
                          boolean boxing,
                          int hitsToWin,
                          boolean sumo,
                          int voidLevel,
                          List<ItemStack> contents,
                          ItemStack helmet,
                          ItemStack chestplate,
                          ItemStack leggings,
                          ItemStack boots,
                          List<PotionEffect> effects) {

    /** Inventory contents as a 36 slot array, ready for {@code PlayerInventory#setStorageContents}. */
    public ItemStack[] storageContents() {
        ItemStack[] slots = new ItemStack[36];
        for (int i = 0; i < contents.size() && i < slots.length; i++) {
            ItemStack item = contents.get(i);
            slots[i] = item == null ? null : item.clone();
        }
        return slots;
    }
}

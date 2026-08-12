package me.alpha432.corepvp.kit.layout;

import me.alpha432.corepvp.kit.Kit;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * A player's own hotbar arrangement for one kit.
 *
 * @param slot   which of the player's layout slots this is (0-based)
 * @param label  the name shown in the editor
 * @param hotbar nine items, in the order the player wants them
 */
public record KitLayout(int slot, String label, ItemStack[] hotbar) {

    public static final int SIZE = 9;

    public static KitLayout empty(int slot) {
        return new KitLayout(slot, "Layout " + (slot + 1), new ItemStack[SIZE]);
    }

    /**
     * Checks a saved layout still matches the kit.
     *
     * <p>Kits are editable by admins, so a layout saved months ago can contain
     * items the kit no longer hands out. Applying it unchecked would quietly
     * give players gear they should not have, so a stale layout is rejected and
     * the kit's own order is used instead.
     */
    public boolean matches(Kit kit) {
        ItemStack[] contents = kit.contents();
        for (ItemStack item : hotbar) {
            if (item == null) {
                continue;
            }
            boolean found = false;
            for (ItemStack candidate : contents) {
                if (candidate != null && candidate.isSimilar(item)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return Arrays.stream(hotbar).allMatch(item -> item == null || item.getType().isAir());
    }
}

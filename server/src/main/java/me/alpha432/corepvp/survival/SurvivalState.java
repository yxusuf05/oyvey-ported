package me.alpha432.corepvp.survival;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A player's survival inventory and vitals, detached from the player. */
public record SurvivalState(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
                            double health, int food, int level, float exp, Location location) {

    public static SurvivalState capture(Player player) {
        return new SurvivalState(
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getLevel(),
                player.getExp(),
                player.getLocation());
    }

    public void applyTo(Player player) {
        player.getInventory().setContents(pad(contents, 36));
        player.getInventory().setArmorContents(pad(armor, 4));
        player.getInventory().setItemInOffHand(offHand);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealth == null ? 20.0D : maxHealth.getValue();
        // A stored health of 0 would kill the player the moment they arrive.
        player.setHealth(Math.max(1.0D, Math.min(cap, health)));
        player.setFoodLevel(food);
        player.setLevel(level);
        player.setExp(Math.max(0.0F, Math.min(1.0F, exp)));
        player.updateInventory();
    }

    /** Decoded arrays can be shorter than the inventory expects. */
    private static ItemStack[] pad(ItemStack[] items, int size) {
        ItemStack[] padded = new ItemStack[size];
        if (items != null) {
            System.arraycopy(items, 0, padded, 0, Math.min(size, items.length));
        }
        return padded;
    }
}

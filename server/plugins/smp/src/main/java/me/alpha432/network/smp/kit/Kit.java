package me.alpha432.network.smp.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * A set of items a player can claim.
 *
 * @param cooldownSeconds 0 means the kit can only be claimed once
 * @param firstJoin       handed out automatically when the player joins the SMP for the first time
 */
public record Kit(String id,
                  String displayName,
                  Material icon,
                  String permission,
                  long cooldownSeconds,
                  boolean firstJoin,
                  double price,
                  List<ItemStack> items,
                  ItemStack helmet,
                  ItemStack chestplate,
                  ItemStack leggings,
                  ItemStack boots,
                  ItemStack offHand,
                  List<PotionEffect> effects) {

    public boolean isOneTime() {
        return cooldownSeconds <= 0;
    }
}

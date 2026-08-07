package me.alpha432.network.smp.crate;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One possible outcome of a crate.
 *
 * @param weight    relative chance; a reward with weight 10 is twice as likely as one with 5
 * @param commands  console commands run after the items are handed out, {@code %player%} is
 *                  replaced with the winner's name
 * @param broadcast whether the whole server is told about this win
 */
public record CrateReward(String id,
                          String displayName,
                          ItemStack display,
                          List<ItemStack> items,
                          List<String> commands,
                          double weight,
                          boolean broadcast) {

    /** Share of this reward among all rewards of the crate, as a percentage. */
    public double chance(double totalWeight) {
        return totalWeight <= 0 ? 0 : weight / totalWeight * 100.0D;
    }
}

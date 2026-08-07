package me.alpha432.network.smp.crate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A crate: a block at spawn that players open with a matching key.
 *
 * @param block where the crate stands; {@code null} until an admin runs {@code /crate setblock}
 */
public final class Crate {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final ItemStack keyTemplate;
    private final List<CrateReward> rewards;
    private Location block;

    public Crate(String id, String displayName, Material icon, ItemStack keyTemplate,
                 List<CrateReward> rewards, Location block) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.keyTemplate = keyTemplate;
        this.rewards = rewards;
        this.block = block;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public ItemStack keyTemplate() {
        return keyTemplate;
    }

    public List<CrateReward> rewards() {
        return rewards;
    }

    public Location block() {
        return block == null ? null : block.clone();
    }

    public void block(Location block) {
        this.block = block == null ? null : block.clone();
    }

    public double totalWeight() {
        double total = 0;
        for (CrateReward reward : rewards) {
            total += reward.weight();
        }
        return total;
    }
}

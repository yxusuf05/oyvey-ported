package me.alpha432.corepvp.match.snapshot;

import me.alpha432.corepvp.match.MatchPlayerStats;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A player's state at the moment a match ended.
 *
 * <p>Items are kept as the raw byte form Paper produces, which preserves
 * enchantments, custom names and data components. Rebuilding them field by
 * field silently loses all of that.
 */
public record PlayerSnapshot(UUID uuid, String name, byte[] contents, byte[] armor,
                             double health, double maxHealth, int food, boolean winner,
                             int hits, int longestCombo, int potionsThrown, int potionsHit,
                             int crystalsPlaced, int totemsPopped) {

    public static PlayerSnapshot capture(Player player, MatchPlayerStats stats, boolean winner) {
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? 20.0D
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        return new PlayerSnapshot(
                player.getUniqueId(),
                player.getName(),
                ItemStack.serializeItemsAsBytes(player.getInventory().getContents()),
                ItemStack.serializeItemsAsBytes(player.getInventory().getArmorContents()),
                player.getHealth(),
                maxHealth,
                player.getFoodLevel(),
                winner,
                stats.hits(),
                stats.longestCombo(),
                stats.potionsThrown(),
                stats.potionsHit(),
                stats.crystalsPlaced(),
                stats.totemsPopped());
    }

    /** Whether this player won is only known once the match is decided. */
    public PlayerSnapshot withWinner(boolean winner) {
        return new PlayerSnapshot(uuid, name, contents, armor, health, maxHealth, food, winner,
                hits, longestCombo, potionsThrown, potionsHit, crystalsPlaced, totemsPopped);
    }

    public ItemStack[] inventoryItems() {
        return ItemStack.deserializeItemsFromBytes(contents);
    }

    public ItemStack[] armorItems() {
        return ItemStack.deserializeItemsFromBytes(armor);
    }

    public int potionAccuracy() {
        return potionsThrown == 0 ? 0 : (int) Math.round(100.0D * potionsHit / potionsThrown);
    }
}

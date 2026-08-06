package me.alpha432.network.core.rank;

import org.bukkit.Material;

import java.util.List;

/**
 * A permission group with a chat/tab prefix.
 *
 * @param weight      higher weights sort further up in the tab list and win in chat formatting
 * @param price       cost in the in-game currency; only meaningful when {@code purchasable}
 * @param purchasable whether players can buy this rank with {@code /rank buy}
 * @param staff       staff ranks are never purchasable and are assigned by the owner only
 * @param perks       human readable perk lines shown in the rank menu
 */
public record Rank(String id,
                   String displayName,
                   String prefix,
                   String suffix,
                   String nameColor,
                   int weight,
                   double price,
                   boolean purchasable,
                   boolean staff,
                   Material icon,
                   List<String> perks,
                   List<String> permissions,
                   List<String> inherits) {

    public static Rank fallback(String id) {
        return new Rank(id, id, "<gray>", "", "<gray>", 0, 0, false, false,
                Material.PAPER, List.of(), List.of(), List.of());
    }
}

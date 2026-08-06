package me.alpha432.network.core.rank;

import java.util.List;

/**
 * A permission group with a chat/tab prefix.
 *
 * @param weight higher weights sort further up in the tab list and win in chat formatting
 */
public record Rank(String id,
                   String displayName,
                   String prefix,
                   String suffix,
                   String nameColor,
                   int weight,
                   List<String> permissions,
                   List<String> inherits) {

    public static Rank fallback(String id) {
        return new Rank(id, id, "<gray>", "", "<gray>", 0, List.of(), List.of());
    }
}

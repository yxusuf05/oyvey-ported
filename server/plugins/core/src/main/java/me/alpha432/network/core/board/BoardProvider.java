package me.alpha432.network.core.board;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Supplies the sidebar content for a player. Several plugins register providers; the one with
 * the highest priority that applies to the player wins, which lets a practice match temporarily
 * replace the normal lobby or SMP board.
 */
public interface BoardProvider {

    /** Higher wins. The default boards use 0, a running match uses 100. */
    default int priority() {
        return 0;
    }

    boolean appliesTo(Player player);

    Component title(Player player);

    /** Top to bottom, at most 15 lines. */
    List<Component> lines(Player player);
}

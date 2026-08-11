package me.alpha432.corepvp.rank;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * A rank in the chat, tab list and above players' heads.
 *
 * @param id         lower-case key, also what is stored on the profile
 * @param display    human readable name
 * @param prefix     MiniMessage prefix including its trailing space
 * @param nameColor  named colour applied to the player's name, e.g. {@code green}
 * @param priority   higher wins when a player qualifies for several ranks;
 *                   also drives tab list ordering
 * @param permission permission that grants this rank, or null for the default
 */
public record Rank(String id, String display, String prefix, String nameColor,
                   int priority, String permission) {

    /**
     * Every player gets their own scoreboard team, which is what gives us
     * per-player name tags and tab entries. Clients sort teams alphabetically,
     * so inverting the priority into the name sorts the tab list by rank.
     */
    public String teamName(String playerName) {
        return String.format("%03d_%s", Math.max(0, 999 - priority), playerName);
    }

    public NamedTextColor color() {
        NamedTextColor color = NamedTextColor.NAMES.value(nameColor == null ? "" : nameColor);
        return color == null ? NamedTextColor.GRAY : color;
    }
}

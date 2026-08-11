package me.alpha432.corepvp.board;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/**
 * One player's sidebar.
 *
 * <p>Each line is a score with a fixed, never-changing entry string whose
 * displayed text comes from {@link Score#customName(Component)}, and whose red
 * number is suppressed with {@link NumberFormat#blank()}. Because entries are
 * never removed and re-added while visible, the sidebar cannot flicker - which
 * is what the old 16-character team prefix/suffix trick existed to work around.
 * Only lines whose content actually changed are written.
 */
public final class PlayerBoard {

    private static final int MAX_LINES = 15;
    private static final String[] ENTRIES = new String[MAX_LINES];

    static {
        // Unique, zero-width entry strings: invisible even if a client ever
        // fails to apply the custom name.
        String codes = "0123456789abcde";
        for (int i = 0; i < MAX_LINES; i++) {
            ENTRIES[i] = "§" + codes.charAt(i) + "§r";
        }
    }

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Component[] shown = new Component[MAX_LINES];

    private Component currentTitle = Component.empty();
    private int activeLines;
    private boolean visible = true;

    public PlayerBoard(Player player) {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("corepvp", Criteria.DUMMY, Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
    }

    public Scoreboard scoreboard() {
        return scoreboard;
    }

    public void update(Component title, java.util.List<Component> lines) {
        if (!visible) {
            return;
        }
        if (!title.equals(currentTitle)) {
            objective.displayName(title);
            currentTitle = title;
        }

        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            Component line = lines.get(i);
            if (line.equals(shown[i])) {
                continue;
            }
            Score score = objective.getScore(ENTRIES[i]);
            // Higher score sorts higher, so line 0 must have the largest value.
            score.setScore(MAX_LINES - i);
            score.numberFormat(NumberFormat.blank());
            score.customName(line);
            shown[i] = line;
        }

        // Only shrink when the board actually got shorter.
        for (int i = count; i < activeLines; i++) {
            scoreboard.resetScores(ENTRIES[i]);
            shown[i] = null;
        }
        activeLines = count;
    }

    /** Hides or shows the sidebar without destroying it. */
    public void visible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        objective.setDisplaySlot(visible ? DisplaySlot.SIDEBAR : null);
    }

    public boolean visible() {
        return visible;
    }

    public void clear() {
        for (int i = 0; i < activeLines; i++) {
            scoreboard.resetScores(ENTRIES[i]);
            shown[i] = null;
        }
        activeLines = 0;
    }
}

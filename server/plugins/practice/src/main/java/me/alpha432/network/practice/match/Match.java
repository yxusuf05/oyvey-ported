package me.alpha432.network.practice.match;

import me.alpha432.network.core.util.BlockRestore;
import me.alpha432.network.practice.arena.Arena;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One duel between two players. */
public final class Match {

    /** Lifecycle of a duel. */
    public enum State {
        /** Countdown is running, players cannot move or hit. */
        STARTING,
        /** The actual fight. */
        FIGHTING,
        /** Somebody won; the result is being shown. */
        ENDING
    }

    private final UUID id = UUID.randomUUID();
    private final PracticeKit kit;
    private final Arena arena;
    private final boolean ranked;
    private final UUID first;
    private final UUID second;
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, Integer> hits = new HashMap<>();
    private final Map<UUID, Integer> combo = new HashMap<>();
    private final BlockRestore blocks = new BlockRestore();
    private final long createdAt = System.currentTimeMillis();

    private State state = State.STARTING;
    private long startedAt;
    private UUID winner;

    public Match(PracticeKit kit, Arena arena, boolean ranked, UUID first, UUID second) {
        this.kit = kit;
        this.arena = arena;
        this.ranked = ranked;
        this.first = first;
        this.second = second;
    }

    public UUID id() {
        return id;
    }

    public PracticeKit kit() {
        return kit;
    }

    public Arena arena() {
        return arena;
    }

    public boolean isRanked() {
        return ranked;
    }

    public UUID first() {
        return first;
    }

    public UUID second() {
        return second;
    }

    public boolean contains(UUID uuid) {
        return first.equals(uuid) || second.equals(uuid);
    }

    /** The other fighter of the pair. */
    public UUID opponentOf(UUID uuid) {
        return first.equals(uuid) ? second : first;
    }

    public Player playerOf(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    public Set<UUID> spectators() {
        return spectators;
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
        if (state == State.FIGHTING && startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
    }

    public boolean isFighting() {
        return state == State.FIGHTING;
    }

    /** Seconds since the countdown ended. */
    public long durationSeconds() {
        long reference = startedAt == 0L ? createdAt : startedAt;
        return (System.currentTimeMillis() - reference) / 1000L;
    }

    public String durationText() {
        long seconds = durationSeconds();
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Counts a landed hit and returns the attacker's new hit total. */
    public int registerHit(UUID attacker, UUID victim) {
        combo.merge(attacker, 1, Integer::sum);
        combo.put(victim, 0);
        return hits.merge(attacker, 1, Integer::sum);
    }

    public int hitsOf(UUID uuid) {
        return hits.getOrDefault(uuid, 0);
    }

    public int comboOf(UUID uuid) {
        return combo.getOrDefault(uuid, 0);
    }

    public BlockRestore blocks() {
        return blocks;
    }

    public UUID winner() {
        return winner;
    }

    public void winner(UUID winner) {
        this.winner = winner;
    }
}

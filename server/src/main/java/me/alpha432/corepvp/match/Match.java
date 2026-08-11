package me.alpha432.corepvp.match;

import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.match.snapshot.PlayerSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One running match.
 *
 * <p>There is deliberately no subclass per mode. A match is a list of teams,
 * and the win condition is "one team left standing" - which is 1v1, 2v2 and
 * party free-for-all at once. {@link MatchType} only changes messaging, ELO and
 * which arena is picked.
 */
public final class Match {

    private final UUID id = UUID.randomUUID();
    private final String shortId;
    private final Kit kit;
    private final Arena arena;
    private final MatchType type;
    private final List<MatchTeam> teams;
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, MatchPlayerStats> stats = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> recorded = new HashMap<>();

    private MatchState state = MatchState.STARTING;
    private int ticks;
    private int countdown;
    private long startedAt;
    private long endedAt;
    private MatchTeam winner;

    public Match(String shortId, Kit kit, Arena arena, MatchType type, List<MatchTeam> teams, int countdownSeconds) {
        this.shortId = shortId;
        this.kit = kit;
        this.arena = arena;
        this.type = type;
        this.teams = List.copyOf(teams);
        this.countdown = countdownSeconds;
        for (MatchTeam team : teams) {
            for (UUID member : team.members()) {
                stats.put(member, new MatchPlayerStats());
            }
        }
    }

    public UUID id() {
        return id;
    }

    /** Short, typeable id used by the post-match inventory command. */
    public String shortId() {
        return shortId;
    }

    public Kit kit() {
        return kit;
    }

    public Arena arena() {
        return arena;
    }

    public MatchType type() {
        return type;
    }

    public List<MatchTeam> teams() {
        return teams;
    }

    public MatchState state() {
        return state;
    }

    public void state(MatchState state) {
        this.state = state;
        if (state == MatchState.FIGHTING && startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
        if (state == MatchState.ENDING && endedAt == 0L) {
            endedAt = System.currentTimeMillis();
        }
        this.ticks = 0;
    }

    public int ticks() {
        return ticks;
    }

    public void tick() {
        ticks++;
    }

    public int countdown() {
        return countdown;
    }

    public int decrementCountdown() {
        return --countdown;
    }

    public long durationMillis() {
        if (startedAt == 0L) {
            return 0L;
        }
        return (endedAt == 0L ? System.currentTimeMillis() : endedAt) - startedAt;
    }

    public MatchTeam winner() {
        return winner;
    }

    public void winner(MatchTeam winner) {
        this.winner = winner;
    }

    public MatchTeam teamOf(UUID uuid) {
        for (MatchTeam team : teams) {
            if (team.contains(uuid)) {
                return team;
            }
        }
        return null;
    }

    public boolean sameTeam(UUID a, UUID b) {
        MatchTeam team = teamOf(a);
        return team != null && team.contains(b);
    }

    public List<MatchTeam> aliveTeams() {
        List<MatchTeam> alive = new ArrayList<>();
        for (MatchTeam team : teams) {
            if (!team.eliminated()) {
                alive.add(team);
            }
        }
        return alive;
    }

    public boolean contains(UUID uuid) {
        return teamOf(uuid) != null;
    }

    public MatchPlayerStats stats(UUID uuid) {
        return stats.computeIfAbsent(uuid, key -> new MatchPlayerStats());
    }

    /**
     * A dead player's inventory is cleared the instant they are moved to
     * spectator, so their final state has to be captured at death rather than
     * at the end of the match.
     */
    public void recordSnapshot(UUID uuid, PlayerSnapshot snapshot) {
        recorded.put(uuid, snapshot);
    }

    public PlayerSnapshot recordedSnapshot(UUID uuid) {
        return recorded.get(uuid);
    }

    public Map<UUID, MatchPlayerStats> allStats() {
        return Map.copyOf(stats);
    }

    public Set<UUID> spectators() {
        return Set.copyOf(spectators);
    }

    public void addSpectator(UUID uuid) {
        spectators.add(uuid);
    }

    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    /** Every participant, dead or alive, that is still online. */
    public List<Player> participants() {
        List<Player> players = new ArrayList<>();
        for (MatchTeam team : teams) {
            players.addAll(team.players());
        }
        return players;
    }

    /** Participants plus spectators - everyone who should see match messages. */
    public List<Player> audience() {
        List<Player> players = participants();
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public void broadcast(Component message) {
        for (Player player : audience()) {
            player.sendMessage(message);
        }
    }

    /** The team a player is not on. Only meaningful for two-team matches. */
    public MatchTeam opposingTeam(MatchTeam team) {
        for (MatchTeam other : teams) {
            if (other != team) {
                return other;
            }
        }
        return null;
    }
}

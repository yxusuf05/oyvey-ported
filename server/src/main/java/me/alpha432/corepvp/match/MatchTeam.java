package me.alpha432.corepvp.match;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One side of a match.
 *
 * <p>1v1 is two teams of one, 2v2 is two teams of two, a party free-for-all is
 * n teams of one. Modelling every match as a list of teams means the win
 * condition, death handling and messaging have a single implementation.
 */
public final class MatchTeam {

    private final int index;
    private final List<UUID> members;
    private final Set<UUID> alive;
    private final NamedTextColor color;

    public MatchTeam(int index, List<UUID> members, NamedTextColor color) {
        this.index = index;
        this.members = List.copyOf(members);
        this.alive = new LinkedHashSet<>(members);
        this.color = color;
    }

    public int index() {
        return index;
    }

    public List<UUID> members() {
        return members;
    }

    public Set<UUID> alive() {
        return Set.copyOf(alive);
    }

    public NamedTextColor color() {
        return color;
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isAlive(UUID uuid) {
        return alive.contains(uuid);
    }

    public void kill(UUID uuid) {
        alive.remove(uuid);
    }

    public boolean eliminated() {
        return alive.isEmpty();
    }

    public int size() {
        return members.size();
    }

    /** Online members only; someone may have disconnected mid-match. */
    public List<Player> players() {
        List<Player> players = new ArrayList<>(members.size());
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public List<Player> alivePlayers() {
        List<Player> players = new ArrayList<>(alive.size());
        for (UUID uuid : alive) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public String displayName() {
        List<Player> players = players();
        if (players.size() == 1) {
            return players.get(0).getName();
        }
        if (players.isEmpty()) {
            return "Team " + (index + 1);
        }
        return players.get(0).getName() + " +" + (players.size() - 1);
    }
}

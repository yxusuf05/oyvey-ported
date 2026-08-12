package me.alpha432.corepvp.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A group of players who queue and fight together. */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> invited = new LinkedHashSet<>();
    private boolean open;

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public void add(UUID uuid) {
        members.add(uuid);
        invited.remove(uuid);
    }

    public void remove(UUID uuid) {
        members.remove(uuid);
    }

    public void invite(UUID uuid) {
        invited.add(uuid);
    }

    public boolean isInvited(UUID uuid) {
        return open || invited.contains(uuid);
    }

    public void revoke(UUID uuid) {
        invited.remove(uuid);
    }

    public boolean open() {
        return open;
    }

    public void open(boolean open) {
        this.open = open;
    }

    /**
     * Hands leadership to the longest-standing remaining member. Without this a
     * party is stranded the moment its leader disconnects.
     */
    public boolean promoteNext() {
        for (UUID member : members) {
            if (!member.equals(leader)) {
                leader = member;
                return true;
            }
        }
        return false;
    }

    public void leader(UUID leader) {
        if (members.contains(leader)) {
            this.leader = leader;
        }
    }

    public List<Player> onlineMembers() {
        List<Player> players = new ArrayList<>(members.size());
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }
}

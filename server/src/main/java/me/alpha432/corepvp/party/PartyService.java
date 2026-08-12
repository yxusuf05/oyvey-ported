package me.alpha432.corepvp.party;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.match.MatchType;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.profile.ProfileSettings;
import me.alpha432.corepvp.state.PlayerState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Parties, and the two things they exist for: team matches and party events. */
public final class PartyService {

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Map<UUID, Party> byId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byPlayer = new ConcurrentHashMap<>();

    public PartyService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    public Party partyOf(Player player) {
        UUID id = byPlayer.get(player.getUniqueId());
        return id == null ? null : byId.get(id);
    }

    public int count() {
        return byId.size();
    }

    // ------------------------------------------------------------------
    //  Membership
    // ------------------------------------------------------------------

    public Party create(Player leader) {
        if (partyOf(leader) != null) {
            messages.send(leader, "party.already-in");
            return null;
        }
        Party party = new Party(leader.getUniqueId());
        byId.put(party.id(), party);
        byPlayer.put(leader.getUniqueId(), party.id());
        messages.send(leader, "party.created");
        return party;
    }

    public void invite(Player leader, Player target) {
        Party party = partyOf(leader);
        if (party == null) {
            party = create(leader);
            if (party == null) {
                return;
            }
        }
        if (!party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "party.not-leader");
            return;
        }
        if (party.contains(target.getUniqueId())) {
            messages.send(leader, "party.already-member", Messages.of("player", target.getName()));
            return;
        }
        int max = plugin.configs().main().getInt("party.max-size", 8);
        if (party.size() >= max) {
            messages.send(leader, "party.full", Messages.of("max", max));
            return;
        }

        Profile profile = plugin.profiles().get(target);
        if (profile != null && !profile.settings().get(ProfileSettings.Flag.PARTY_INVITES)) {
            messages.send(leader, "party.invites-off", Messages.of("player", target.getName()));
            return;
        }

        party.invite(target.getUniqueId());
        messages.send(leader, "party.invited", Messages.of("player", target.getName()));
        messages.send(target, "party.invite-received", Messages.of("player", leader.getName()));
    }

    public void join(Player player, Player leader) {
        Party party = partyOf(leader);
        if (party == null) {
            messages.send(player, "party.no-such-party", Messages.of("player", leader.getName()));
            return;
        }
        if (partyOf(player) != null) {
            messages.send(player, "party.already-in");
            return;
        }
        if (!party.isInvited(player.getUniqueId())) {
            messages.send(player, "party.not-invited");
            return;
        }
        int max = plugin.configs().main().getInt("party.max-size", 8);
        if (party.size() >= max) {
            messages.send(player, "party.full", Messages.of("max", max));
            return;
        }

        party.add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), party.id());
        broadcast(party, messages.render("party.joined", Messages.of("player", player.getName())));
    }

    public void leave(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            messages.send(player, "party.not-in");
            return;
        }
        removeMember(party, player.getUniqueId(), "party.left");
        messages.send(player, "party.you-left");
    }

    public void kick(Player leader, Player target) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "party.not-leader");
            return;
        }
        if (!party.contains(target.getUniqueId()) || target.equals(leader)) {
            messages.send(leader, "party.not-member", Messages.of("player", target.getName()));
            return;
        }
        removeMember(party, target.getUniqueId(), "party.kicked");
        messages.send(target, "party.you-kicked");
    }

    public void disband(Player leader) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "party.not-leader");
            return;
        }
        broadcast(party, messages.render("party.disbanded"));
        for (UUID member : party.members()) {
            byPlayer.remove(member);
        }
        byId.remove(party.id());
    }

    private void removeMember(Party party, UUID uuid, String messageKey) {
        party.remove(uuid);
        byPlayer.remove(uuid);

        Player player = plugin.getServer().getPlayer(uuid);
        String name = player == null ? uuid.toString().substring(0, 8) : player.getName();
        broadcast(party, messages.render(messageKey, Messages.of("player", name)));

        if (party.size() == 0) {
            byId.remove(party.id());
            return;
        }
        if (party.isLeader(uuid) || !party.contains(party.leader())) {
            if (party.promoteNext()) {
                Player leader = plugin.getServer().getPlayer(party.leader());
                broadcast(party, messages.render("party.new-leader",
                        Messages.of("player", leader == null ? "?" : leader.getName())));
            }
        }
    }

    public void handleQuit(Player player) {
        Party party = partyOf(player);
        if (party != null) {
            removeMember(party, player.getUniqueId(), "party.left");
        }
    }

    // ------------------------------------------------------------------
    //  Playing together
    // ------------------------------------------------------------------

    /** Splits the party into two balanced teams and starts a match. */
    public void split(Player leader, Kit kit) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "party.not-leader");
            return;
        }
        List<Player> members = availableMembers(party);
        if (members.size() < 2) {
            messages.send(leader, "party.need-two");
            return;
        }

        Collections.shuffle(members);
        List<Player> sideA = new ArrayList<>();
        List<Player> sideB = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            (i % 2 == 0 ? sideA : sideB).add(members.get(i));
        }

        Arena arena = plugin.arenas().acquire(kit);
        if (arena == null) {
            messages.send(leader, "party.no-arena", Messages.of("kit", kit.displayName()));
            return;
        }
        plugin.matches().create(kit, MatchType.PARTY_SPLIT, arena, List.of(sideA, sideB));
    }

    /** Everyone against everyone: one team per member. */
    public void freeForAll(Player leader, Kit kit) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "party.not-leader");
            return;
        }
        List<Player> members = availableMembers(party);
        if (members.size() < 2) {
            messages.send(leader, "party.need-two");
            return;
        }

        Arena arena = plugin.arenas().acquire(kit);
        if (arena == null) {
            messages.send(leader, "party.no-arena", Messages.of("kit", kit.displayName()));
            return;
        }
        List<List<Player>> sides = new ArrayList<>();
        members.forEach(member -> sides.add(List.of(member)));
        plugin.matches().create(kit, MatchType.PARTY_FFA, arena, sides);
    }

    /** Members who are actually free to be pulled into a match. */
    private List<Player> availableMembers(Party party) {
        List<Player> available = new ArrayList<>();
        for (Player member : party.onlineMembers()) {
            PlayerState state = plugin.states().state(member);
            if (state == PlayerState.LOBBY || state == PlayerState.QUEUE) {
                available.add(member);
            }
        }
        return available;
    }

    public void chat(Player player, String message) {
        Party party = partyOf(player);
        if (party == null) {
            messages.send(player, "party.not-in");
            return;
        }
        broadcast(party, messages.render("party.chat",
                Messages.of("player", player.getName()), Messages.of("message", message)));
    }

    public void broadcast(Party party, Component message) {
        party.onlineMembers().forEach(member -> member.sendMessage(message));
    }
}

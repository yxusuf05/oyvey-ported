package me.alpha432.corepvp.duel;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.arena.Arena;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.match.MatchType;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.profile.ProfileSettings;
import me.alpha432.corepvp.state.PlayerState;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Direct challenges between two players. */
public final class DuelService {

    private record Request(UUID from, String kitId, long expiresAt) {
    }

    private final CorePvPPlugin plugin;
    private final Messages messages;
    /** target -> sender -> request */
    private final Map<UUID, Map<UUID, Request>> pending = new ConcurrentHashMap<>();

    public DuelService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    private long expirySeconds() {
        return plugin.configs().main().getLong("duel.request-expiry-seconds", 30L);
    }

    public void send(Player from, Player target, Kit kit) {
        if (from.equals(target)) {
            messages.send(from, "duel.self");
            return;
        }
        if (!inLobby(from) || !inLobby(target)) {
            messages.send(from, "duel.busy", Messages.of("player", target.getName()));
            return;
        }

        Profile targetProfile = plugin.profiles().get(target);
        if (targetProfile != null && !targetProfile.settings().get(ProfileSettings.Flag.DUEL_REQUESTS)) {
            messages.send(from, "duel.disabled", Messages.of("player", target.getName()));
            return;
        }

        pending.computeIfAbsent(target.getUniqueId(), key -> new ConcurrentHashMap<>())
                .put(from.getUniqueId(),
                        new Request(from.getUniqueId(), kit.id(),
                                System.currentTimeMillis() + expirySeconds() * 1000L));

        messages.send(from, "duel.sent",
                Messages.of("player", target.getName()), Messages.of("kit", kit.displayName()));
        messages.send(target, "duel.received",
                Messages.of("player", from.getName()),
                Messages.of("kit", kit.displayName()),
                Messages.of("seconds", expirySeconds()));
    }

    public void accept(Player target, Player from) {
        Map<UUID, Request> requests = pending.get(target.getUniqueId());
        Request request = requests == null ? null : requests.remove(from.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            messages.send(target, "duel.none", Messages.of("player", from.getName()));
            return;
        }
        if (!inLobby(from) || !inLobby(target)) {
            messages.send(target, "duel.busy", Messages.of("player", from.getName()));
            return;
        }

        Kit kit = plugin.kits().byId(request.kitId());
        if (kit == null) {
            messages.send(target, "duel.kit-gone");
            return;
        }

        // The arena is taken before anything else is changed, so a full server
        // leaves both players in the hub instead of half-started.
        Arena arena = plugin.arenas().acquire(kit);
        if (arena == null) {
            messages.send(target, "duel.no-arena", Messages.of("kit", kit.displayName()));
            messages.send(from, "duel.no-arena", Messages.of("kit", kit.displayName()));
            return;
        }

        plugin.matches().create(kit, MatchType.DUEL, arena, List.of(List.of(from), List.of(target)));
    }

    /** Requests this player has received that have not expired. */
    public List<String> sendersFor(Player target) {
        Map<UUID, Request> requests = pending.get(target.getUniqueId());
        if (requests == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        requests.values().removeIf(request -> request.expiresAt() < now);
        return requests.keySet().stream()
                .map(uuid -> plugin.getServer().getPlayer(uuid))
                .filter(player -> player != null && player.isOnline())
                .map(Player::getName)
                .toList();
    }

    public void forget(UUID uuid) {
        pending.remove(uuid);
        pending.values().forEach(map -> map.remove(uuid));
    }

    private boolean inLobby(Player player) {
        PlayerState state = plugin.states().state(player);
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE;
    }
}

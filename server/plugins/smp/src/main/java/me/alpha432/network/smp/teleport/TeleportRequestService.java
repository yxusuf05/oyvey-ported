package me.alpha432.network.smp.teleport;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pending {@code /tpa} and {@code /tpahere} requests. Requests time out on their own. */
public final class TeleportRequestService implements Listener {

    /** Which way the teleport goes once the request is accepted. */
    public enum Direction {
        /** The sender travels to the target. */
        TO_TARGET,
        /** The target travels to the sender. */
        TO_SENDER
    }

    public record Request(UUID sender, UUID target, Direction direction, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /** target -> requests waiting for them. */
    private final Map<UUID, List<Request>> incoming = new HashMap<>();
    private final long timeoutMillis;

    public TeleportRequestService(long timeoutSeconds) {
        this.timeoutMillis = timeoutSeconds * 1000L;
    }

    /** @return false when the same request is already pending. */
    public boolean add(Player sender, Player target, Direction direction) {
        List<Request> requests = incoming.computeIfAbsent(target.getUniqueId(), id -> new ArrayList<>());
        requests.removeIf(Request::isExpired);
        for (Request request : requests) {
            if (request.sender().equals(sender.getUniqueId()) && request.direction() == direction) {
                return false;
            }
        }
        requests.add(new Request(sender.getUniqueId(), target.getUniqueId(), direction,
                System.currentTimeMillis() + timeoutMillis));
        return true;
    }

    /**
     * Takes the request from {@code senderName}, or the newest one when no name is given.
     *
     * @return {@code null} when nothing is pending
     */
    public Request take(Player target, String senderName) {
        List<Request> requests = incoming.get(target.getUniqueId());
        if (requests == null) {
            return null;
        }
        requests.removeIf(Request::isExpired);
        if (requests.isEmpty()) {
            return null;
        }
        if (senderName == null) {
            return requests.remove(requests.size() - 1);
        }
        for (int i = requests.size() - 1; i >= 0; i--) {
            Player sender = Bukkit.getPlayer(requests.get(i).sender());
            if (sender != null && sender.getName().equalsIgnoreCase(senderName)) {
                return requests.remove(i);
            }
        }
        return null;
    }

    public List<Request> pending(Player target) {
        List<Request> requests = incoming.getOrDefault(target.getUniqueId(), List.of());
        requests.removeIf(Request::isExpired);
        return requests;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        incoming.remove(uuid);
        incoming.values().forEach(list -> list.removeIf(request -> request.sender().equals(uuid)));
    }
}

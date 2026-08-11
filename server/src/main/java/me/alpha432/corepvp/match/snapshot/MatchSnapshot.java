package me.alpha432.corepvp.match.snapshot;

import java.util.List;
import java.util.Locale;

/** Everyone's end-of-match state, addressable by a short id players can type. */
public record MatchSnapshot(String shortId, String kitName, long capturedAt, List<PlayerSnapshot> players) {

    public PlayerSnapshot byName(String name) {
        for (PlayerSnapshot snapshot : players) {
            if (snapshot.name().equalsIgnoreCase(name)) {
                return snapshot;
            }
        }
        return null;
    }

    public List<String> names() {
        return players.stream().map(PlayerSnapshot::name).toList();
    }

    public String id() {
        return shortId.toLowerCase(Locale.ROOT);
    }
}

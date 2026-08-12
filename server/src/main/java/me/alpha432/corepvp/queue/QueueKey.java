package me.alpha432.corepvp.queue;

import java.util.Locale;

/** Identifies one queue: a kit, ranked or not, at a given team size. */
public record QueueKey(String kitId, boolean ranked, int teamSize) {

    public QueueKey {
        kitId = kitId.toLowerCase(Locale.ROOT);
    }

    public static QueueKey solo(String kitId, boolean ranked) {
        return new QueueKey(kitId, ranked, 1);
    }

    @Override
    public String toString() {
        return kitId + (ranked ? ":ranked" : ":unranked") + ":" + teamSize;
    }
}

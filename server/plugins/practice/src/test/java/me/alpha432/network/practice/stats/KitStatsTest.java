package me.alpha432.network.practice.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitStatsTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void winsRaiseTheRatingAndTheStreak() {
        KitStats stats = KitStats.fresh(player, "nodebuff", 1000);

        stats.won(16);
        stats.won(14);

        assertEquals(1030, stats.elo());
        assertEquals(2, stats.wins());
        assertEquals(2, stats.streak());
        assertEquals(2, stats.bestStreak());
    }

    @Test
    void aLossResetsTheStreakButKeepsTheBest() {
        KitStats stats = KitStats.fresh(player, "nodebuff", 1000);
        stats.won(16);
        stats.won(16);

        stats.lost(20);

        assertEquals(0, stats.streak());
        assertEquals(2, stats.bestStreak());
        assertEquals(1012, stats.elo());
        assertEquals(1, stats.losses());
    }

    @Test
    void theRatingStopsAtTheFloor() {
        KitStats stats = KitStats.fresh(player, "sumo", 10);

        stats.lost(500);

        assertEquals(Elo.FLOOR, stats.elo());
    }

    @Test
    void winRateIsZeroWithoutMatches() {
        KitStats stats = KitStats.fresh(player, "boxing", 1000);

        assertEquals(0.0D, stats.winRate(), 0.0001D);

        stats.won(10);
        stats.lost(10);
        stats.lost(10);

        assertEquals(1.0D / 3.0D, stats.winRate(), 0.0001D);
    }

    @Test
    void casualResultsMoveTheRecordButNotTheRating() {
        KitStats stats = KitStats.fresh(player, "classic", 1000);

        stats.won(0);
        stats.lost(0);

        assertEquals(1000, stats.elo());
        assertEquals(1, stats.wins());
        assertEquals(1, stats.losses());
    }
}

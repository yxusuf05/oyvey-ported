package me.alpha432.network.practice.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloTest {

    private static final int K = 32;

    @Test
    void equalRatingsSplitTheOdds() {
        assertEquals(0.5D, Elo.expected(1000, 1000), 0.0001D);
        assertEquals(16, Elo.gain(1000, 1000, K));
    }

    @Test
    void beatingAStrongerPlayerPaysMore() {
        int underdogGain = Elo.gain(1000, 1400, K);
        int favouriteGain = Elo.gain(1400, 1000, K);

        assertTrue(underdogGain > favouriteGain,
                "the underdog should gain more than the favourite");
        assertTrue(underdogGain <= K);
    }

    @Test
    void theWinnerAlwaysMoves() {
        // Even a hopeless mismatch has to be worth at least one point.
        assertEquals(1, Elo.gain(3000, 100, K));
    }

    @Test
    void gainAndLossMatchAwayFromTheFloor() {
        assertEquals(Elo.gain(1200, 1100, K), Elo.loss(1200, 1100, K));
    }

    @Test
    void lossNeverPushesBelowTheFloor() {
        // Two evenly matched players just above the floor: the raw delta would be ~16, but the
        // loser only has 3 points left to give.
        assertEquals(3, Elo.loss(0, 3, K));
        assertEquals(0, Elo.loss(1500, Elo.FLOOR, K));
        assertTrue(Elo.loss(1500, 5, K) <= 5, "a player at rating 5 can lose at most 5 points");
    }

    @Test
    void tiersFollowTheRating() {
        assertEquals("bronze", Elo.tier(900));
        assertEquals("silber", Elo.tier(1100));
        assertEquals("gold", Elo.tier(1300));
        assertEquals("diamant", Elo.tier(1600));
        assertEquals("champion", Elo.tier(2000));
        assertEquals("champion", Elo.tier(5000));
    }
}

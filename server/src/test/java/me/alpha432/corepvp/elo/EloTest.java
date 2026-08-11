package me.alpha432.corepvp.elo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloTest {

    @Test
    void equalRatingsAreACoinFlip() {
        assertEquals(0.5D, Elo.expected(1000, 1000), 1e-9);
        assertEquals(0.5D, Elo.expected(2400, 2400), 1e-9);
    }

    @Test
    void expectationIsSymmetric() {
        assertEquals(1.0D, Elo.expected(1200, 900) + Elo.expected(900, 1200), 1e-9);
    }

    @Test
    void fourHundredPointsIsTenToOne() {
        // The definition of the scale: +400 means ten times as likely to win.
        assertEquals(10.0D / 11.0D, Elo.expected(1400, 1000), 1e-6);
    }

    @Test
    void beatingSomeoneStrongerIsWorthMore() {
        int underdog = Elo.gain(1000, 1400, 32);
        int even = Elo.gain(1000, 1000, 32);
        int favourite = Elo.gain(1400, 1000, 32);
        assertTrue(underdog > even, "an upset must be worth more than an even win");
        assertTrue(even > favourite, "beating a much weaker player must be worth less");
    }

    @Test
    void anEvenMatchMovesHalfTheKFactor() {
        assertEquals(16, Elo.gain(1000, 1000, 32));
    }

    @Test
    void aHopelessWinStillMovesTheRating() {
        // Without a floor, a 2000 beating a 100 would gain literally nothing and
        // the loser would never drop.
        assertTrue(Elo.gain(3000, 100, 32) >= 1);
    }

    @Test
    void ratingNeverGoesNegative() {
        assertEquals(0, Elo.applyLoss(10, 40));
        assertEquals(0, Elo.applyLoss(0, 1));
        assertEquals(960, Elo.applyLoss(1000, 40));
    }

    @Test
    void newPlayersMoveFasterAndVeteransSlower() {
        assertEquals(64, Elo.kFactor(3, 1000, 32), "placement matches move fast");
        assertEquals(32, Elo.kFactor(50, 1000, 32));
        assertEquals(16, Elo.kFactor(500, 2100, 32), "high ratings are damped");
    }
}

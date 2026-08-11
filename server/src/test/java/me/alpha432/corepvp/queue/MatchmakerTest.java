package me.alpha432.corepvp.queue;

import me.alpha432.corepvp.queue.Matchmaker.Pairing;
import me.alpha432.corepvp.queue.Matchmaker.Ticket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakerTest {

    private final Matchmaker matchmaker = new Matchmaker();
    private final EloRangePolicy policy = new EloRangePolicy(50, 25, 5000);

    private static Ticket ticket(int elo, long enqueuedAt) {
        return new Ticket(UUID.randomUUID(), elo, enqueuedAt);
    }

    // ------------------------------------------------------------------
    //  Unranked
    // ------------------------------------------------------------------

    @Test
    void unrankedPairsInArrivalOrder() {
        Ticket first = ticket(2000, 100L);
        Ticket second = ticket(100, 200L);
        Ticket third = ticket(1500, 300L);
        Ticket fourth = ticket(1500, 400L);

        List<Pairing> pairings = matchmaker.pairUnranked(List.of(third, first, fourth, second));

        assertEquals(2, pairings.size());
        assertEquals(first, pairings.get(0).a());
        assertEquals(second, pairings.get(0).b());
        assertEquals(third, pairings.get(1).a());
        assertEquals(fourth, pairings.get(1).b());
    }

    @Test
    void anOddQueueLeavesExactlyOneWaiting() {
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            tickets.add(ticket(1000, i));
        }
        List<Pairing> pairings = matchmaker.pairUnranked(tickets);
        assertEquals(3, pairings.size());
        assertEquals(6, countPaired(pairings));
    }

    @Test
    void anEmptyOrSingleQueueProducesNothing() {
        assertTrue(matchmaker.pairUnranked(List.of()).isEmpty());
        assertTrue(matchmaker.pairUnranked(List.of(ticket(1000, 0L))).isEmpty());
        assertTrue(matchmaker.pairRanked(List.of(), 0L, policy).isEmpty());
        assertTrue(matchmaker.pairRanked(List.of(ticket(1000, 0L)), 0L, policy).isEmpty());
    }

    // ------------------------------------------------------------------
    //  Ranked
    // ------------------------------------------------------------------

    @Test
    void rankedPairsTheClosestRatings() {
        Ticket low = ticket(1000, 0L);
        Ticket alsoLow = ticket(1020, 0L);
        Ticket high = ticket(1800, 0L);
        Ticket alsoHigh = ticket(1810, 0L);

        List<Pairing> pairings = matchmaker.pairRanked(List.of(low, high, alsoLow, alsoHigh), 0L, policy);

        assertEquals(2, pairings.size());
        for (Pairing pairing : pairings) {
            assertTrue(Math.abs(pairing.a().elo() - pairing.b().elo()) <= 50,
                    "should pair neighbours, not across the ladder");
        }
    }

    @Test
    void ratingsOutsideTheBaseRangeDoNotPairImmediately() {
        Ticket low = ticket(1000, 0L);
        Ticket high = ticket(1600, 0L);
        assertTrue(matchmaker.pairRanked(List.of(low, high), 0L, policy).isEmpty());
    }

    @Test
    void waitingWidensTheRangeUntilTheyPair() {
        Ticket low = ticket(1000, 0L);
        Ticket high = ticket(1600, 0L);
        // 600 points apart: needs (600 - 50) / 25 = 22 seconds of waiting.
        assertTrue(matchmaker.pairRanked(List.of(low, high), 21_000L, policy).isEmpty());
        assertEquals(1, matchmaker.pairRanked(List.of(low, high), 22_000L, policy).size());
    }

    @Test
    void aLongWaitCanReachSomeoneWhoJustJoined() {
        // The important asymmetry: without taking the *other* side's widened
        // range into account, a quiet server never matches its two players.
        Ticket waiting = ticket(800, 0L);
        Ticket fresh = ticket(1600, 120_000L);

        List<Pairing> pairings = matchmaker.pairRanked(List.of(waiting, fresh), 120_000L, policy);
        assertEquals(1, pairings.size(), "the long-waiting player's range must count for both sides");
    }

    @Test
    void longestWaitingIsServedFirst() {
        Ticket veteran = ticket(1000, 0L);
        Ticket newcomerA = ticket(1010, 90_000L);
        Ticket newcomerB = ticket(1020, 95_000L);

        List<Pairing> pairings = matchmaker.pairRanked(
                List.of(newcomerA, veteran, newcomerB), 100_000L, policy);

        assertEquals(1, pairings.size());
        Pairing pairing = pairings.get(0);
        assertTrue(pairing.a() == veteran || pairing.b() == veteran,
                "the player who waited longest must be in the pairing");
    }

    @Test
    void nobodyIsPairedTwice() {
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tickets.add(ticket(900 + i * 10, i * 1000L));
        }
        List<Pairing> pairings = matchmaker.pairRanked(tickets, 60_000L, policy);

        Set<UUID> seen = new HashSet<>();
        for (Pairing pairing : pairings) {
            assertTrue(seen.add(pairing.a().id()), "ticket paired twice");
            assertTrue(seen.add(pairing.b().id()), "ticket paired twice");
        }
        assertEquals(pairings.size() * 2, seen.size());
    }

    @Test
    void aPairingNeverContainsTheSameTicketOnBothSides() {
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            tickets.add(ticket(1000, 0L));
        }
        for (Pairing pairing : matchmaker.pairRanked(tickets, 0L, policy)) {
            assertNotNull(pairing.a());
            assertNotNull(pairing.b());
            assertTrue(pairing.a() != pairing.b());
        }
    }

    private int countPaired(List<Pairing> pairings) {
        Set<UUID> ids = new HashSet<>();
        for (Pairing pairing : pairings) {
            ids.add(pairing.a().id());
            ids.add(pairing.b().id());
        }
        return ids.size();
    }
}

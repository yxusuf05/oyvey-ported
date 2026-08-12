package me.alpha432.corepvp.crystal;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttributionTrackerTest {

    private final AtomicLong clock = new AtomicLong();
    private final AttributionTracker tracker = new AttributionTracker(30_000L, 2_000L, clock::get);

    @Test
    void remembersWhoPlacedACrystal() {
        UUID crystal = UUID.randomUUID();
        UUID placer = UUID.randomUUID();
        tracker.rememberEntity(crystal, placer);
        assertEquals(placer, tracker.ownerOfEntity(crystal));
    }

    @Test
    void forgetsACrystalOnceItIsTooOldToBlame() {
        UUID crystal = UUID.randomUUID();
        tracker.rememberEntity(crystal, UUID.randomUUID());
        clock.set(30_000L);
        assertEquals(1, tracker.trackedEntities());
        clock.set(30_001L);
        assertNull(tracker.ownerOfEntity(crystal));
    }

    @Test
    void unknownCrystalsHaveNoOwner() {
        assertNull(tracker.ownerOfEntity(UUID.randomUUID()));
    }

    @Test
    void findsTheAnchorThatWentOffNearby() {
        UUID owner = UUID.randomUUID();
        tracker.rememberPosition(100, 64, 100, owner);
        assertEquals(owner, tracker.ownerNear(102, 64, 100, 8.0D));
    }

    @Test
    void ignoresDetonationsOutOfRange() {
        tracker.rememberPosition(0, 64, 0, UUID.randomUUID());
        assertNull(tracker.ownerNear(100, 64, 100, 8.0D));
    }

    @Test
    void ignoresDetonationsThatAreTooOld() {
        tracker.rememberPosition(0, 64, 0, UUID.randomUUID());
        clock.set(2_001L);
        assertNull(tracker.ownerNear(0, 64, 0, 8.0D));
    }

    @Test
    void twoSimultaneousAnchorsCreditTheCloserOne() {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        tracker.rememberPosition(0, 64, 0, far);
        tracker.rememberPosition(10, 64, 0, near);
        assertEquals(near, tracker.ownerNear(9, 64, 0, 16.0D));
        assertEquals(far, tracker.ownerNear(1, 64, 0, 16.0D));
    }

    @Test
    void atEqualDistanceTheMoreRecentOneWins() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        tracker.rememberPosition(5, 64, 0, older);
        clock.set(500L);
        tracker.rememberPosition(5, 64, 0, newer);
        assertEquals(newer, tracker.ownerNear(5, 64, 0, 8.0D));
    }

    @Test
    void sweepDropsExpiredEntries() {
        tracker.rememberEntity(UUID.randomUUID(), UUID.randomUUID());
        tracker.rememberPosition(0, 0, 0, UUID.randomUUID());
        assertEquals(1, tracker.trackedEntities());
        assertEquals(1, tracker.trackedPositions());

        clock.set(2_500L);
        tracker.sweep();
        assertEquals(1, tracker.trackedEntities(), "crystals live longer than anchor positions");
        assertEquals(0, tracker.trackedPositions());

        clock.set(31_000L);
        tracker.sweep();
        assertEquals(0, tracker.trackedEntities());
    }
}

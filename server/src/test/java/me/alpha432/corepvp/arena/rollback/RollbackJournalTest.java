package me.alpha432.corepvp.arena.rollback;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollbackJournalTest {

    @Test
    void recordsTheFirstStateAndIgnoresLaterOnes() {
        RollbackJournal<String> journal = new RollbackJournal<>();
        long key = BlockKey.pack(10, 64, 10);

        assertTrue(journal.record(key, "air"), "first record should be accepted");
        assertFalse(journal.record(key, "obsidian"), "second record should be ignored");
        assertFalse(journal.record(key, "cobblestone"));

        assertEquals("air", journal.get(key), "the pre-match state must survive later changes");
        assertEquals(1, journal.size());
    }

    @Test
    void restoresFromTheTopDown() {
        RollbackJournal<String> journal = new RollbackJournal<>();
        journal.record(BlockKey.pack(0, 64, 0), "low");
        journal.record(BlockKey.pack(0, 120, 0), "high");
        journal.record(BlockKey.pack(0, 90, 0), "middle");
        journal.record(BlockKey.pack(0, -64, 0), "bedrock");

        List<String> order = journal.restoreOrder().stream().map(Map.Entry::getValue).toList();
        assertEquals(List.of("high", "middle", "low", "bedrock"), order);
    }

    @Test
    void restoreOrderIsStableAcrossCalls() {
        RollbackJournal<String> journal = new RollbackJournal<>();
        for (int i = 0; i < 50; i++) {
            journal.record(BlockKey.pack(i, i % 20, -i), "block" + i);
        }
        assertEquals(journal.restoreOrder(), journal.restoreOrder(),
                "a restore that resumes after a crash must repeat the same order");
    }

    @Test
    void separatePositionsAreTrackedSeparately() {
        RollbackJournal<String> journal = new RollbackJournal<>();
        journal.record(BlockKey.pack(1, 64, 1), "a");
        journal.record(BlockKey.pack(1, 64, 2), "b");
        journal.record(BlockKey.pack(2, 64, 1), "c");
        journal.record(BlockKey.pack(1, 65, 1), "d");

        assertEquals(4, journal.size());
        assertEquals("a", journal.get(BlockKey.pack(1, 64, 1)));
        assertEquals("d", journal.get(BlockKey.pack(1, 65, 1)));
    }

    @Test
    void clearEmptiesTheJournal() {
        RollbackJournal<String> journal = new RollbackJournal<>();
        journal.record(BlockKey.pack(0, 0, 0), "x");
        assertFalse(journal.isEmpty());
        journal.clear();
        assertTrue(journal.isEmpty());
        assertEquals(0, journal.size());
        assertTrue(journal.record(BlockKey.pack(0, 0, 0), "y"), "a cleared journal accepts the position again");
    }
}

package dev.hyperionfps.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThoriumImmediateBatcherTest {

    @BeforeEach
    void setUp() {
        ThoriumImmediateBatcher.setEnabled(true);
        ThoriumImmediateBatcher.resetStats();
        ThoriumImmediateBatcher.flushActiveBatch();
    }

    @Test
    void testImmediateBatchingCoalescesContiguousTextDraws() {
        // First text draw starts the batch sequence
        boolean batched1 = ThoriumImmediateBatcher.shouldBatchDraw("text_normal");
        assertFalse(batched1, "First draw starts the sequence and flushes prior state");

        // Second and third contiguous text draws are coalesced
        boolean batched2 = ThoriumImmediateBatcher.shouldBatchDraw("text_normal");
        boolean batched3 = ThoriumImmediateBatcher.shouldBatchDraw("text_see_through");

        assertTrue(batched2, "Second text draw should be coalesced into batch");
        assertTrue(batched3, "Third text draw should be coalesced into batch");
        assertEquals(2, ThoriumImmediateBatcher.getSavedDraws());
    }

    @Test
    void testStateChangeFlushesBatch() {
        ThoriumImmediateBatcher.shouldBatchDraw("text");
        ThoriumImmediateBatcher.shouldBatchDraw("text");
        assertEquals(1, ThoriumImmediateBatcher.getSavedDraws());

        // A non-UI render type interrupts and flushes the batch
        boolean batched = ThoriumImmediateBatcher.shouldBatchDraw("entity_translucent");
        assertFalse(batched);

        ThoriumImmediateBatcher.flushActiveBatch();
    }
}

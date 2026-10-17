package dev.hyperionfps.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ThoriumChunkUploadPacerTest {

    @Test
    void testBudgetedUploadsCapsPerFrame() {
        ThoriumChunkUploadPacer.setEnabled(true);
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger counter = new AtomicInteger();

        // Enqueue 20 tasks
        for (int i = 0; i < 20; i++) {
            queue.add(counter::incrementAndGet);
        }

        ThoriumChunkUploadPacer.runBudgetedUploads(queue);

        // Should have executed at most MAX_SECTIONS_PER_FRAME (8) tasks
        assertEquals(8, counter.get(), "First frame should execute at most 8 uploads");
        assertEquals(12, queue.size(), "12 tasks should remain in the queue for subsequent frames");

        // Execute next frame
        ThoriumChunkUploadPacer.runBudgetedUploads(queue);
        assertEquals(16, counter.get());
        assertEquals(4, queue.size());

        // Execute third frame
        ThoriumChunkUploadPacer.runBudgetedUploads(queue);
        assertEquals(20, counter.get());
        assertTrue(queue.isEmpty());
    }

    @Test
    void testEmptyQueue() {
        Queue<Runnable> queue = new ArrayDeque<>();
        ThoriumChunkUploadPacer.runBudgetedUploads(queue);
        ThoriumChunkUploadPacer.runBudgetedUploads(null);
        assertTrue(queue.isEmpty());
    }
}

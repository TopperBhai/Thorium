package dev.hyperionfps.lighting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncLightEngineTest {

    private AsyncLightEngine engine;

    @BeforeEach
    void setUp() {
        engine = AsyncLightEngine.getInstance();
        engine.clearQueue();
        engine.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        engine.clearQueue();
    }

    @Test
    void testEnqueueAndBatchProcess() {
        // Enqueue 50 light updates
        for (int i = 0; i < 50; i++) {
            engine.enqueue(1000L + i, AsyncLightEngine.LIGHT_TYPE_BLOCK, (i % 15) + 1);
        }

        assertEquals(50, engine.getPendingCount());

        AtomicInteger processed = new AtomicInteger(0);
        int count = engine.processBatch(30, (pos, type, level) -> {
            processed.incrementAndGet();
            assertEquals(AsyncLightEngine.LIGHT_TYPE_BLOCK, type);
            assertTrue(level >= 1 && level <= 15);
        });

        assertEquals(30, count);
        assertEquals(30, processed.get());
        assertEquals(20, engine.getPendingCount());

        // Process remaining 20
        count = engine.processBatch(30, (pos, type, level) -> processed.incrementAndGet());
        assertEquals(20, count);
        assertEquals(50, processed.get());
        assertEquals(0, engine.getPendingCount());
    }

    @Test
    void testEngineLifecycle() {
        engine.init();
        assertTrue(engine.isRunning());

        // Ensure worker thread is alive
        engine.enqueue(5555L, AsyncLightEngine.LIGHT_TYPE_SKY, 15);
        assertTrue(engine.getQueuedUpdates() > 0);

        engine.shutdown();
        assertFalse(engine.isRunning());
    }

    @Test
    void testDisabledEngineIgnoresEnqueue() {
        engine.setEnabled(false);
        assertFalse(engine.isEnabled());

        engine.enqueue(9999L, AsyncLightEngine.LIGHT_TYPE_BLOCK, 10);
        assertEquals(0, engine.getPendingCount());
    }
}

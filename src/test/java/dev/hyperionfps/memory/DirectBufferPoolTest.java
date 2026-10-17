package dev.hyperionfps.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class DirectBufferPoolTest {

    @Test
    void warmedPoolReusesTheSameBufferObject() {
        DirectBufferPool pool = new DirectBufferPool("test", 12, 16, 3);

        ByteBuffer first = pool.acquire(1000);
        assertEquals(4096, first.capacity());
        assertTrue(pool.release(first));

        ByteBuffer second = pool.acquire(1000);
        assertSame(first, second);
        assertEquals(4096, second.capacity());

        DirectBufferPool.Counters counters = pool.counters();
        assertEquals(1, counters.hits());
        assertEquals(1, counters.misses());
    }

    @Test
    void perClassLimitBoundsRetainedBuffers() {
        DirectBufferPool pool = new DirectBufferPool("test", 12, 16, 1);

        ByteBuffer kept = pool.acquire(4096);
        ByteBuffer overflow = pool.acquire(4096);
        assertTrue(pool.release(kept));
        assertTrue(pool.release(overflow)); // exceeds the per-class limit: freed, not queued

        assertEquals(1, pool.counters().discarded());
        assertSame(kept, pool.acquire(4096)); // the retained buffer is the one served again
    }

    @Test
    void oversizedRequestsBypassThePoolAndAreNotOwned() {
        DirectBufferPool pool = new DirectBufferPool("test", 12, 16, 3);

        ByteBuffer big = pool.acquire(1 << 18);
        assertEquals(1 << 18, big.capacity());
        assertEquals(1, pool.counters().oversized());
        assertFalse(pool.release(big));
    }

    @Test
    void foreignAndHeapBuffersAreRefusedUntouched() {
        DirectBufferPool pool = new DirectBufferPool("test", 12, 16, 3);

        assertFalse(pool.release(ByteBuffer.allocateDirect(3000))); // not a size class
        assertFalse(pool.release(ByteBuffer.allocate(16)));         // heap buffer
        assertEquals(0, pool.counters().hits());
        assertEquals(0, pool.counters().discarded());
    }

    @Test
    void trimIdleReleasesQueuedBuffersOnlyOnceIdle() {
        DirectBufferPool pool = new DirectBufferPool("test", 12, 16, 3);

        ByteBuffer buffer = pool.acquire(4096);
        pool.release(buffer);

        assertEquals(0, pool.trimIdle(Long.MAX_VALUE)); // budget not exhausted yet
        assertEquals(1, pool.trimIdle(0L));             // zero budget: everything is idle
        assertEquals(1, pool.counters().trimmed());
    }
}

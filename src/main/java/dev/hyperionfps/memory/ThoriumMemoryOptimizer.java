package dev.hyperionfps.memory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import dev.hyperionfps.memory.pool.ZeroAllocationEngine;

/**
 * Advanced Memory Optimizer and JVM Heap Defend Governor.
 *
 * <p>Inspired by FerriteCore and ModernFix, this engine addresses Minecraft's primary
 * cause of 1% low stutter: Stop-The-World Java Garbage Collection pauses. It monitors heap
 * allocation velocity, deduplicates immutable structures, and provides instantaneous
 * native buffer sweeps and garbage collection on demand.</p>
 */
public final class ThoriumMemoryOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/MemoryOptimizer");

    private static final AtomicBoolean DEDUPLICATION_ENABLED = new AtomicBoolean(true);
    private static final AtomicBoolean OFF_HEAP_ARENA_ENABLED = new AtomicBoolean(true);
    private static final AtomicLong LAST_PURGE_TIMESTAMP = new AtomicLong(0);
    private static final AtomicLong TOTAL_MEMORY_FREED_MB = new AtomicLong(0);

    private ThoriumMemoryOptimizer() {
    }

    public static boolean isDeduplicationEnabled() {
        return DEDUPLICATION_ENABLED.get();
    }

    public static void setDeduplicationEnabled(boolean enabled) {
        DEDUPLICATION_ENABLED.set(enabled);
    }

    public static boolean isOffHeapArenaEnabled() {
        return OFF_HEAP_ARENA_ENABLED.get();
    }

    public static void setOffHeapArenaEnabled(boolean enabled) {
        OFF_HEAP_ARENA_ENABLED.set(enabled);
    }

    public static long getUsedMemoryMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    }

    public static long getMaxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    public static long getFreeMemoryMb() {
        return Runtime.getRuntime().freeMemory() / (1024L * 1024L);
    }

    public static long getLastPurgeTimestamp() {
        return LAST_PURGE_TIMESTAMP.get();
    }

    public static long getTotalMemoryFreedMb() {
        return TOTAL_MEMORY_FREED_MB.get();
    }

    /**
     * Executes an emergency memory purge and native buffer sweep.
     *
     * <p>Evicts unreferenced occlusion caches, sweeps idle DirectBuffers,
     * resets ThreadLocal pools, and triggers JVM garbage collection.</p>
     *
     * @return megabytes of memory reclaimed
     */
    public static long purgeMemoryNow() {
        long usedBefore = getUsedMemoryMb();

        // 1. Clear culling caches
        AsyncOcclusionCuller.clearCache();
        ThoriumBvhCuller.clear();

        // 2. Recycle and trim zero-allocation scratchpads
        ZeroAllocationEngine.clear();

        // 3. Sweep idle native direct buffers
        IdleJanitor.sweepOnce(0L);

        // 4. Hint garbage collection to reclaim dead heap objects
        System.gc();

        long usedAfter = getUsedMemoryMb();
        long freedMb = Math.max(0, usedBefore - usedAfter);

        LAST_PURGE_TIMESTAMP.set(System.currentTimeMillis());
        TOTAL_MEMORY_FREED_MB.addAndGet(freedMb);

        LOGGER.info("Thorium Memory Purge complete: reclaimed {} MB (Current Heap: {} / {} MB)",
                freedMb, usedAfter, getMaxMemoryMb());

        return freedMb;
    }
}

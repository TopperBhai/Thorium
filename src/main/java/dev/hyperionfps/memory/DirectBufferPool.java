package dev.hyperionfps.memory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Size-classed pool of direct {@link ByteBuffer}s - the zero-allocation
 * substrate for the render stages that follow this phase.
 *
 * <p>Requests are rounded up to powers of two between 4&nbsp;KiB and
 * 1&nbsp;MiB and served from per-class LIFO queues, so a warmed pool satisfies
 * steady-state acquires with no allocation at all and no direct-memory
 * pressure on the garbage collector. Two escape hatches keep memory bounded:
 * each size class keeps at most a fixed number of buffers (further releases
 * are freed immediately rather than queued), and {@link IdleJanitor} trims
 * every queue once the pool has been quiet for longer than the idle
 * budget.</p>
 *
 * <p>Ownership contract: {@link #acquire(int)} hands out a cleared buffer,
 * {@link #release(ByteBuffer)} takes it back. After a successful release the
 * caller must not touch the buffer again - the pool may have freed its native
 * memory deterministically. Buffers the pool does not recognize as its own
 * (wrong capacity, oversized) are refused untouched, never freed, so a caller
 * mistake can never pull memory out from under unrelated code.</p>
 */
public final class DirectBufferPool implements IdleJanitor.Trimable {

    /** Smallest size class: 4 KiB. */
    public static final int DEFAULT_MIN_CAPACITY_SHIFT = 12;
    /** Largest size class: 1 MiB. */
    public static final int DEFAULT_MAX_CAPACITY_SHIFT = 20;
    /** Buffers retained per size class before releases start freeing directly. */
    public static final int DEFAULT_PER_CLASS_LIMIT = 4;

    /** Immutable snapshot of a pool's lifetime counters. */
    public record Counters(long hits, long misses, long oversized, long discarded, long trimmed) {
    }

    private final String name;
    private final int minShift;
    private final int maxShift;
    private final int perClassLimit;
    private final List<Deque<ByteBuffer>> buckets;

    private volatile long lastUsedNanos;

    private long hits;
    private long misses;
    private long oversized;
    private long discarded;
    private long trimmed;

    public DirectBufferPool(String name) {
        this(name, DEFAULT_MIN_CAPACITY_SHIFT, DEFAULT_MAX_CAPACITY_SHIFT, DEFAULT_PER_CLASS_LIMIT);
    }

    public DirectBufferPool(String name, int minCapacityShift, int maxCapacityShift, int perClassLimit) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Pool name is required");
        }
        if (minCapacityShift < 0 || maxCapacityShift < minCapacityShift || maxCapacityShift > 30) {
            throw new IllegalArgumentException("Invalid capacity shift range: " + minCapacityShift + ".." + maxCapacityShift);
        }
        if (perClassLimit < 1) {
            throw new IllegalArgumentException("perClassLimit must be positive: " + perClassLimit);
        }
        this.name = name;
        this.minShift = minCapacityShift;
        this.maxShift = maxCapacityShift;
        this.perClassLimit = perClassLimit;
        this.buckets = new ArrayList<>(maxCapacityShift - minCapacityShift + 1);
        for (int shift = minCapacityShift; shift <= maxCapacityShift; shift++) {
            buckets.add(new ArrayDeque<>());
        }
        this.lastUsedNanos = System.nanoTime();
    }

    /** A cleared direct buffer of at least {@code minCapacity} bytes. */
    public synchronized ByteBuffer acquire(int minCapacity) {
        int requested = Math.max(minCapacity, 1);
        lastUsedNanos = System.nanoTime();
        int shift = capacityShift(requested);
        if (shift > maxShift) {
            oversized++;
            return ByteBuffer.allocateDirect(requested);
        }
        Deque<ByteBuffer> bucket = buckets.get(shift - minShift);
        ByteBuffer buffer = bucket.pollFirst();
        if (buffer == null) {
            misses++;
            return ByteBuffer.allocateDirect(1 << shift);
        }
        hits++;
        buffer.clear();
        return buffer;
    }

    /**
     * Returns a buffer to the pool, or refuses it when it is not a size class
     * this pool hands out. When the owning size class is already full the
     * buffer is freed immediately instead of queued.
     *
     * @return {@code true} if the pool took ownership of the buffer
     */
    public synchronized boolean release(ByteBuffer buffer) {
        lastUsedNanos = System.nanoTime();
        if (buffer == null || !buffer.isDirect()) {
            return false;
        }
        int shift = capacityShift(buffer.capacity());
        if (shift > maxShift || (1 << shift) != buffer.capacity()) {
            // Foreign allocation (or oversized): leave it untouched rather than
            // risk freeing memory that belongs to someone else.
            return false;
        }
        Deque<ByteBuffer> bucket = buckets.get(shift - minShift);
        if (bucket.size() >= perClassLimit) {
            discarded++;
            OffHeap.releaseDirect(buffer);
            return true;
        }
        buffer.clear();
        bucket.addFirst(buffer);
        return true;
    }

    public String name() {
        return name;
    }

    public synchronized Counters counters() {
        return new Counters(hits, misses, oversized, discarded, trimmed);
    }

    @Override
    public long lastUsedNanos() {
        return lastUsedNanos;
    }

    @Override
    public synchronized int trimIdle(long idleBudgetNanos) {
        if (System.nanoTime() - lastUsedNanos < idleBudgetNanos) {
            return 0;
        }
        int released = 0;
        for (Deque<ByteBuffer> bucket : buckets) {
            ByteBuffer buffer;
            while ((buffer = bucket.pollFirst()) != null) {
                OffHeap.releaseDirect(buffer);
                released++;
            }
        }
        trimmed += released;
        return released;
    }

    @Override
    public synchronized String toString() {
        return "DirectBufferPool[" + name + " " + counters() + "]";
    }

    /** Smallest size-class shift whose capacity covers {@code capacity}. */
    private int capacityShift(int capacity) {
        int shift = 32 - Integer.numberOfLeadingZeros(capacity - 1);
        return Math.max(minShift, shift);
    }
}

package dev.hyperionfps.particle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Zero-allocation object and direct off-heap buffer pool for high-throughput particle batching.
 *
 * <p>Preallocates recycled {@link ParticleQuad} instances and a contiguous direct NIO
 * buffer to eliminate GC pauses during heavy particle bursts (lava pools, fireworks, explosions).</p>
 */
public final class ParticleBatchPool {

    public static final int DEFAULT_MAX_QUADS = 8192; // 8,192 quads = 32,768 vertices
    private final int maxQuads;

    private final ParticleQuad[] quadPool;
    private int poolIndex = 0;

    private final ByteBuffer directByteBuffer;
    private final FloatBuffer directFloatBuffer;

    public ParticleBatchPool() {
        this(DEFAULT_MAX_QUADS);
    }

    public ParticleBatchPool(int maxQuads) {
        this.maxQuads = Math.max(16, maxQuads);
        this.quadPool = new ParticleQuad[this.maxQuads];
        for (int i = 0; i < this.maxQuads; i++) {
            this.quadPool[i] = new ParticleQuad();
        }

        int bufferBytes = this.maxQuads * ParticleQuad.FLOATS_PER_QUAD * Float.BYTES;
        this.directByteBuffer = ByteBuffer.allocateDirect(bufferBytes).order(ByteOrder.nativeOrder());
        this.directFloatBuffer = this.directByteBuffer.asFloatBuffer();
    }

    /**
     * Acquires a recycled ParticleQuad from the pool.
     */
    public ParticleQuad acquire() {
        if (poolIndex >= maxQuads) {
            // Buffer full, return the last slot to prevent crashes
            return quadPool[maxQuads - 1];
        }
        return quadPool[poolIndex++];
    }

    /**
     * Returns direct FloatBuffer ready for writing.
     */
    public FloatBuffer getDirectFloatBuffer() {
        return directFloatBuffer;
    }

    /**
     * Resets the acquisition index for the next batch/frame.
     */
    public void reset() {
        poolIndex = 0;
        directFloatBuffer.clear();
    }

    public int getAcquiredCount() {
        return poolIndex;
    }

    public int getMaxQuads() {
        return maxQuads;
    }
}

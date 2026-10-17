package dev.hyperionfps.memory.pool;

import net.minecraft.world.phys.Vec3;

/**
 * Thread-local ring buffer pool for {@link PooledVec3}.
 */
public final class Vec3Pool {

    private static final int POOL_SIZE = 64;
    private static final int POOL_MASK = POOL_SIZE - 1;

    private static final ThreadLocal<Vec3Pool> THREAD_LOCAL = ThreadLocal.withInitial(Vec3Pool::new);

    private final PooledVec3[] pool;
    private int index;

    private Vec3Pool() {
        this.pool = new PooledVec3[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            this.pool[i] = new PooledVec3();
        }
        this.index = 0;
    }

    public static Vec3Pool get() {
        return THREAD_LOCAL.get();
    }

    public PooledVec3 acquire() {
        PooledVec3 vec = pool[index & POOL_MASK];
        index++;
        return vec.set(0, 0, 0);
    }

    public PooledVec3 acquire(double x, double y, double z) {
        PooledVec3 vec = pool[index & POOL_MASK];
        index++;
        return vec.set(x, y, z);
    }

    public PooledVec3 acquire(Vec3 source) {
        PooledVec3 vec = pool[index & POOL_MASK];
        index++;
        return vec.set(source);
    }
}

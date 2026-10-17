package dev.hyperionfps.memory.pool;

import net.minecraft.core.BlockPos;

/**
 * Thread-local ring buffer pool for {@link BlockPos.MutableBlockPos}.
 *
 * <p>Eliminates millions of short-lived {@code BlockPos} allocations during chunk
 * meshing, occlusion queries, and entity tick checks in the render loop.</p>
 */
public final class BlockPosPool {

    private static final int POOL_SIZE = 64;
    private static final int POOL_MASK = POOL_SIZE - 1;

    private static final ThreadLocal<BlockPosPool> THREAD_LOCAL = ThreadLocal.withInitial(BlockPosPool::new);

    private final BlockPos.MutableBlockPos[] pool;
    private int index;

    private BlockPosPool() {
        this.pool = new BlockPos.MutableBlockPos[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            this.pool[i] = new BlockPos.MutableBlockPos();
        }
        this.index = 0;
    }

    /** Retrieves the current thread's pool. */
    public static BlockPosPool get() {
        return THREAD_LOCAL.get();
    }

    /** Acquires a pooled mutable block position initialized to (0, 0, 0). */
    public BlockPos.MutableBlockPos acquire() {
        BlockPos.MutableBlockPos pos = pool[index & POOL_MASK];
        index++;
        return pos.set(0, 0, 0);
    }

    /** Acquires a pooled mutable block position initialized to integer (x, y, z). */
    public BlockPos.MutableBlockPos acquire(int x, int y, int z) {
        BlockPos.MutableBlockPos pos = pool[index & POOL_MASK];
        index++;
        return pos.set(x, y, z);
    }

    /** Acquires a pooled mutable block position initialized to double (x, y, z). */
    public BlockPos.MutableBlockPos acquire(double x, double y, double z) {
        BlockPos.MutableBlockPos pos = pool[index & POOL_MASK];
        index++;
        return pos.set(x, y, z);
    }
}

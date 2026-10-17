package dev.hyperionfps.memory.pool;

import net.minecraft.core.BlockPos;

/**
 * High-level zero-allocation engine facade for frame loop calculations.
 */
public final class ZeroAllocationEngine {

    private ZeroAllocationEngine() {
    }

    public static BlockPos.MutableBlockPos pooledBlockPos() {
        return BlockPosPool.get().acquire();
    }

    public static BlockPos.MutableBlockPos pooledBlockPos(int x, int y, int z) {
        return BlockPosPool.get().acquire(x, y, z);
    }

    public static BlockPos.MutableBlockPos pooledBlockPos(double x, double y, double z) {
        return BlockPosPool.get().acquire(x, y, z);
    }

    public static PooledVec3 pooledVec3() {
        return Vec3Pool.get().acquire();
    }

    public static PooledVec3 pooledVec3(double x, double y, double z) {
        return Vec3Pool.get().acquire(x, y, z);
    }

    public static PooledMatrix4f pooledMatrix4f() {
        return MatrixPool.get().acquireMatrix();
    }

    public static PooledQuaternion pooledQuaternion() {
        return MatrixPool.get().acquireQuaternion();
    }

    /** Bounded ring buffer pool recycling hook. */
    public static void clear() {
        // Ring buffer pools are naturally bounded; no action required
    }
}

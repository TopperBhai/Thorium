package dev.hyperionfps.memory.pool;

/**
 * Thread-local ring buffer pool for matrices and quaternions.
 */
public final class MatrixPool {

    private static final int POOL_SIZE = 32;
    private static final int POOL_MASK = POOL_SIZE - 1;

    private static final ThreadLocal<MatrixPool> THREAD_LOCAL = ThreadLocal.withInitial(MatrixPool::new);

    private final PooledMatrix4f[] matrixPool;
    private final PooledQuaternion[] quaternionPool;
    private int matrixIndex;
    private int quaternionIndex;

    private MatrixPool() {
        this.matrixPool = new PooledMatrix4f[POOL_SIZE];
        this.quaternionPool = new PooledQuaternion[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            this.matrixPool[i] = new PooledMatrix4f();
            this.quaternionPool[i] = new PooledQuaternion();
        }
    }

    public static MatrixPool get() {
        return THREAD_LOCAL.get();
    }

    public PooledMatrix4f acquireMatrix() {
        PooledMatrix4f mat = matrixPool[matrixIndex & POOL_MASK];
        matrixIndex++;
        return mat.identity();
    }

    public PooledQuaternion acquireQuaternion() {
        PooledQuaternion quat = quaternionPool[quaternionIndex & POOL_MASK];
        quaternionIndex++;
        return quat.identity();
    }
}

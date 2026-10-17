package dev.hyperionfps.memory.pool;

/**
 * Reusable 4-component float quaternion for zero-allocation rotations.
 */
public final class PooledQuaternion {

    public float x;
    public float y;
    public float z;
    public float w;

    public PooledQuaternion() {
        identity();
    }

    public PooledQuaternion identity() {
        this.x = 0.0f;
        this.y = 0.0f;
        this.z = 0.0f;
        this.w = 1.0f;
        return this;
    }

    public PooledQuaternion set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }
}

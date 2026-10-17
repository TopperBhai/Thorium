package dev.hyperionfps.memory.pool;

import java.util.Arrays;

/**
 * Reusable 4x4 float transformation matrix for zero-allocation rendering operations.
 */
public final class PooledMatrix4f {

    public final float[] m = new float[16];

    public PooledMatrix4f() {
        identity();
    }

    public PooledMatrix4f identity() {
        Arrays.fill(m, 0.0f);
        m[0] = 1.0f;
        m[5] = 1.0f;
        m[10] = 1.0f;
        m[15] = 1.0f;
        return this;
    }

    public PooledMatrix4f set(float[] values) {
        System.arraycopy(values, 0, this.m, 0, Math.min(16, values.length));
        return this;
    }

    public PooledMatrix4f translate(float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
        return this;
    }

    public PooledMatrix4f scale(float x, float y, float z) {
        m[0] *= x; m[1] *= x; m[2] *= x; m[3] *= x;
        m[4] *= y; m[5] *= y; m[6] *= y; m[7] *= y;
        m[8] *= z; m[9] *= z; m[10] *= z; m[11] *= z;
        return this;
    }
}

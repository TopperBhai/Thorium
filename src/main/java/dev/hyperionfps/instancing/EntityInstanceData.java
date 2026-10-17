package dev.hyperionfps.instancing;

import java.nio.FloatBuffer;

/**
 * Packed per-instance data for entity instancing and batch rendering.
 *
 * <p>Stores position, rotation, scale, packed light coordinates, and overlay color
 * for zero-allocation instanced buffer uploads. Each instance occupies exactly
 * 8 floats (32 bytes), perfectly cache-line aligned.</p>
 */
public final class EntityInstanceData {

    public static final int FLOATS_PER_INSTANCE = 8;
    public static final int BYTES_PER_INSTANCE = FLOATS_PER_INSTANCE * Float.BYTES; // 32 bytes

    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;
    public float scale;
    public int packedLight;
    public int overlayColor;

    public EntityInstanceData() {
        this.scale = 1.0f;
    }

    public EntityInstanceData set(float x, float y, float z, float yaw, float pitch, float scale, int light, int overlay) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.scale = scale;
        this.packedLight = light;
        this.overlayColor = overlay;
        return this;
    }

    /**
     * Packs this instance's attributes into a float array at the specified offset.
     */
    public void writeTo(float[] array, int offset) {
        array[offset] = this.x;
        array[offset + 1] = this.y;
        array[offset + 2] = this.z;
        array[offset + 3] = this.yaw;
        array[offset + 4] = this.pitch;
        array[offset + 5] = this.scale;
        array[offset + 6] = Float.intBitsToFloat(this.packedLight);
        array[offset + 7] = Float.intBitsToFloat(this.overlayColor);
    }

    /**
     * Writes this instance's attributes into a direct FloatBuffer.
     */
    public void writeTo(FloatBuffer buffer) {
        buffer.put(this.x);
        buffer.put(this.y);
        buffer.put(this.z);
        buffer.put(this.yaw);
        buffer.put(this.pitch);
        buffer.put(this.scale);
        buffer.put(Float.intBitsToFloat(this.packedLight));
        buffer.put(Float.intBitsToFloat(this.overlayColor));
    }
}

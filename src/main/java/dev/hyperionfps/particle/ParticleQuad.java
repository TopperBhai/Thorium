package dev.hyperionfps.particle;

import java.nio.FloatBuffer;

/**
 * High-performance quad representation for particle batching.
 *
 * <p>Each quad consists of 4 vertices. Each vertex contains 3D position (x, y, z),
 * texture coordinates (u, v), RGBA color components, and packed block/sky light.</p>
 */
public final class ParticleQuad {

    public static final int VERTICES_PER_QUAD = 4;
    public static final int FLOATS_PER_VERTEX = 10; // x, y, z, u, v, r, g, b, a, light
    public static final int FLOATS_PER_QUAD = VERTICES_PER_QUAD * FLOATS_PER_VERTEX; // 40 floats

    // Flat vertex array for all 4 vertices (4 * 10 = 40 floats)
    private final float[] data = new float[FLOATS_PER_QUAD];

    public ParticleQuad() {
    }

    /**
     * Sets the attributes of one vertex of this quad.
     *
     * @param vertexIndex 0 to 3
     */
    public void setVertex(int vertexIndex, float x, float y, float z,
                          float u, float v,
                          float r, float g, float b, float a,
                          int light) {
        int offset = vertexIndex * FLOATS_PER_VERTEX;
        data[offset] = x;
        data[offset + 1] = y;
        data[offset + 2] = z;
        data[offset + 3] = u;
        data[offset + 4] = v;
        data[offset + 5] = r;
        data[offset + 6] = g;
        data[offset + 7] = b;
        data[offset + 8] = a;
        data[offset + 9] = Float.intBitsToFloat(light);
    }

    /**
     * Sets the entire quad with center position, half-size, UVs, color, and light.
     */
    public void setBillboard(float centerX, float centerY, float centerZ,
                             float halfSize,
                             float u0, float v0, float u1, float v1,
                             float r, float g, float b, float a,
                             int light) {
        // Vertex 0: Bottom-Left
        setVertex(0, centerX - halfSize, centerY - halfSize, centerZ, u0, v1, r, g, b, a, light);
        // Vertex 1: Bottom-Right
        setVertex(1, centerX + halfSize, centerY - halfSize, centerZ, u1, v1, r, g, b, a, light);
        // Vertex 2: Top-Right
        setVertex(2, centerX + halfSize, centerY + halfSize, centerZ, u1, v0, r, g, b, a, light);
        // Vertex 3: Top-Left
        setVertex(3, centerX - halfSize, centerY + halfSize, centerZ, u0, v0, r, g, b, a, light);
    }

    /**
     * Writes all 40 floats of this quad into the destination FloatBuffer.
     */
    public void writeTo(FloatBuffer buffer) {
        buffer.put(data, 0, FLOATS_PER_QUAD);
    }

    public float[] rawData() {
        return data;
    }
}

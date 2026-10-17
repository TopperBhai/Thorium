package dev.hyperionfps.instancing;

import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * Reusable entity instance bucket grouping entities of identical model/texture state.
 *
 * <p>Preallocates instance slots so that zero heap allocations occur per frame
 * when collecting thousands of mob instances.</p>
 */
public final class EntityGroup {

    private final String groupKey;
    private EntityInstanceData[] instances;
    private int count;

    public EntityGroup(String groupKey) {
        this(groupKey, 128);
    }

    public EntityGroup(String groupKey, int initialCapacity) {
        this.groupKey = Objects.requireNonNull(groupKey, "groupKey cannot be null");
        int cap = Math.max(16, initialCapacity);
        this.instances = new EntityInstanceData[cap];
        for (int i = 0; i < cap; i++) {
            this.instances[i] = new EntityInstanceData();
        }
        this.count = 0;
    }

    /**
     * Records an entity instance into this group using preallocated slots.
     */
    public EntityInstanceData add(float x, float y, float z, float yaw, float pitch, float scale, int light, int overlay) {
        if (count >= instances.length) {
            grow();
        }
        EntityInstanceData data = instances[count++];
        return data.set(x, y, z, yaw, pitch, scale, light, overlay);
    }

    private void grow() {
        int newCap = instances.length * 2;
        EntityInstanceData[] newArr = new EntityInstanceData[newCap];
        System.arraycopy(instances, 0, newArr, 0, instances.length);
        for (int i = instances.length; i < newCap; i++) {
            newArr[i] = new EntityInstanceData();
        }
        this.instances = newArr;
    }

    /**
     * Packs all active instances into a direct FloatBuffer for GPU upload.
     */
    public int pack(FloatBuffer buffer) {
        for (int i = 0; i < count; i++) {
            instances[i].writeTo(buffer);
        }
        return count;
    }

    /**
     * Resets active count to zero for the next frame without deallocating memory.
     */
    public void reset() {
        this.count = 0;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public int getCount() {
        return count;
    }

    public int getCapacity() {
        return instances.length;
    }

    public EntityInstanceData get(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for count " + count);
        }
        return instances[index];
    }
}

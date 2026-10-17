package dev.hyperionfps.instancing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-throughput registry and lifecycle manager for instanced entity groups.
 *
 * <p>Collects mob render states, sorts them by model/texture group key,
 * and maintains reusable instance buckets across render frames.</p>
 */
public final class EntityGroupManager {

    private final Map<String, EntityGroup> groups = new ConcurrentHashMap<>(64);
    private final List<EntityGroup> activeGroupsList = new ArrayList<>(64);

    public EntityGroupManager() {
    }

    /**
     * Prepares all groups for a new render frame by resetting instance counts.
     */
    public void beginFrame() {
        for (EntityGroup group : groups.values()) {
            group.reset();
        }
        activeGroupsList.clear();
    }

    /**
     * Submits an entity instance to its corresponding render group.
     */
    public EntityInstanceData submit(String groupKey, float x, float y, float z,
                                      float yaw, float pitch, float scale,
                                      int light, int overlay) {
        EntityGroup group = groups.computeIfAbsent(groupKey, EntityGroup::new);
        return group.add(x, y, z, yaw, pitch, scale, light, overlay);
    }

    /**
     * Returns an unmodifiable list of groups that contain at least one instance this frame.
     */
    public List<EntityGroup> getActiveGroups() {
        activeGroupsList.clear();
        for (EntityGroup group : groups.values()) {
            if (group.getCount() > 0) {
                activeGroupsList.add(group);
            }
        }
        return Collections.unmodifiableList(activeGroupsList);
    }

    /**
     * Gets or creates an entity group by key.
     */
    public EntityGroup getGroup(String groupKey) {
        return groups.computeIfAbsent(groupKey, EntityGroup::new);
    }

    /**
     * Returns the total number of entity instances submitted in the current frame.
     */
    public int getTotalInstanceCount() {
        int total = 0;
        for (EntityGroup group : groups.values()) {
            total += group.getCount();
        }
        return total;
    }

    /**
     * Returns the number of distinct groups with active instances.
     */
    public int getActiveGroupCount() {
        int active = 0;
        for (EntityGroup group : groups.values()) {
            if (group.getCount() > 0) {
                active++;
            }
        }
        return active;
    }

    /**
     * Returns the total registered groups count.
     */
    public int getRegisteredGroupCount() {
        return groups.size();
    }

    /**
     * Clears all registered groups and releases allocated slots.
     */
    public void clear() {
        groups.clear();
        activeGroupsList.clear();
    }
}

package dev.hyperionfps.culling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/**
 * Beyond-Sodium Hierarchical Bounding Volume (BVH) Spatial Entity Culler.
 *
 * <p>Groups entities by 16x16 chunk columns and evaluates cluster-level visibility.
 * If an entire chunk column is occluded behind terrain or outside the camera frustum,
 * all entities within that chunk are culled in a single O(1) test, avoiding hundreds
 * of individual bounding-box and raycast tests per frame.</p>
 */
public final class ThoriumBvhCuller {

    private static volatile boolean enabled = true;
    private static final AtomicLong CLUSTER_CULLED_ENTITIES = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, Boolean> CHUNK_OCCLUSION_CACHE = new ConcurrentHashMap<>(256);

    private ThoriumBvhCuller() {
    }

    /** Clears cluster occlusion cache, called on frame begin or camera shift. */
    public static void onFrameBegin() {
        if (!CHUNK_OCCLUSION_CACHE.isEmpty()) {
            CHUNK_OCCLUSION_CACHE.clear();
        }
    }

    /** Clears all static cache entries and resets metrics. */
    public static void clear() {
        CHUNK_OCCLUSION_CACHE.clear();
        CLUSTER_CULLED_ENTITIES.set(0);
    }

    /**
     * Evaluates if an entity's parent chunk column is completely occluded.
     * Returns true if the entity should be culled immediately.
     */
    public static boolean isEntityClusterOccluded(Entity entity, double camX, double camY, double camZ, BlockGetter level) {
        if (!enabled || entity == null || level == null) {
            return false;
        }

        int blockX = (int) Math.floor(entity.getX());
        int blockY = (int) Math.floor(entity.getY());
        int blockZ = (int) Math.floor(entity.getZ());

        int secX = blockX >> 4;
        int secY = blockY >> 4;
        int secZ = blockZ >> 4;

        // Proximity guard: Never cluster-cull entities within 12 blocks
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        if ((dx * dx + dy * dy + dz * dz) < 144.0) {
            return false;
        }

        long chunkKey = (((long) secX & 0x3FFFFF) << 42) | (((long) secY & 0xFFFFF) << 21) | ((long) secZ & 0x1FFFFF);
        Boolean cached = CHUNK_OCCLUSION_CACHE.get(chunkKey);
        if (cached != null) {
            if (cached) {
                CLUSTER_CULLED_ENTITIES.incrementAndGet();
            }
            return cached;
        }

        boolean occluded = OcclusionGrid.isSectionOccluded(level, camX, camY, camZ, secX, secY, secZ);
        CHUNK_OCCLUSION_CACHE.put(chunkKey, occluded);

        if (occluded) {
            CLUSTER_CULLED_ENTITIES.incrementAndGet();
        }
        return occluded;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static long getClusterCulledCount() {
        return CLUSTER_CULLED_ENTITIES.get();
    }
}

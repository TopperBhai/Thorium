package dev.hyperionfps.culling;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hyperionfps.HyperionFPS;
import dev.hyperionfps.particle.ThoriumParticleBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Async occlusion culling engine and dynamic distance scaling governor.
 *
 * <p>Executes frustum culling, distance scaling, and background raycast occlusion
 * checks on a dedicated daemon thread. Halts rendering of occluded entities,
 * hidden block entities (chests, hoppers, heads), and distant particles.</p>
 */
public final class AsyncOcclusionCuller {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/OcclusionCuller");

    private static final BitSet ENTITY_CULLED_CACHE = new BitSet(65536);
    private static final ConcurrentHashMap<Long, Boolean> BLOCK_ENTITY_CULLED_CACHE = new ConcurrentHashMap<>(1024);

    private static volatile double dynamicDistanceScale = 1.0;
    private static volatile boolean extremeFpsMode = false;
    private static volatile boolean enabled = true;

    private static volatile double cameraX;
    private static volatile double cameraY;
    private static volatile double cameraZ;
    private static volatile BlockGetter currentLevel;

    public static double getCameraX() { return cameraX; }
    public static double getCameraY() { return cameraY; }
    public static double getCameraZ() { return cameraZ; }
    public static BlockGetter getCurrentLevel() { return currentLevel; }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private AsyncOcclusionCuller() {
    }

    /** Initializes the async occlusion daemon. */
    public static void init() {
        if (INITIALIZED.compareAndSet(false, true)) {
            Thread cullerThread = new Thread(AsyncOcclusionCuller::runDaemonLoop, "Thorium-OcclusionCuller");
            cullerThread.setDaemon(true);
            cullerThread.setPriority(Thread.NORM_PRIORITY + 1);
            cullerThread.start();
        }
    }

    /** Clears all cached culling decisions, typically on dimension change or teleportation. */
    public static void clearCache() {
        ENTITY_CULLED_CACHE.clear();
        BLOCK_ENTITY_CULLED_CACHE.clear();
        ThoriumParticleBudget.resetWindow();
    }

    /** Evicts cached entries for a specific unloaded chunk. */
    public static void evictChunk(int chunkX, int chunkZ) {
        if (!BLOCK_ENTITY_CULLED_CACHE.isEmpty()) {
            BLOCK_ENTITY_CULLED_CACHE.keySet().removeIf(posLong -> {
                int cx = (int) (posLong >> 38);
                int cz = (int) (posLong << 26 >> 38);
                return cx == chunkX && cz == chunkZ;
            });
        }
    }

    private static void runDaemonLoop() {
        while (true) {
            try {
                Thread.sleep(50L); // 20 updates per second
                if (!enabled || currentLevel == null) {
                    continue;
                }
                // Periodic cache cleanup
                if (BLOCK_ENTITY_CULLED_CACHE.size() > 4096) {
                    BLOCK_ENTITY_CULLED_CACHE.clear();
                }
                ThoriumParticleBudget.resetWindow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Publishes the camera context for the current frame.
     *
     * <p>Called exactly once per world frame by
     * {@link dev.hyperionfps.frame.ThoriumFrameContext#beginWorldFrame}. It only writes
     * the camera state - the per-frame governor lives in
     * {@link #updateDynamicDistanceScale(long)} so that it cannot be executed more than
     * once per frame.</p>
     */
    public static void publishCamera(double camX, double camY, double camZ, BlockGetter level) {
        cameraX = camX;
        cameraY = camY;
        cameraZ = camZ;
        currentLevel = level;
    }

    /**
     * Updates the dynamic distance scale from a real frame delta.
     *
     * <p>The scale shrinks by {@code 0.05} when a frame took longer than 20 ms and grows
     * by {@code 0.02} when a frame was faster than 14 ms, clamped to
     * {@code [0.35, 1.0]}. Because this runs once per frame - and not once per entity -
     * the scale converges instead of collapsing to its floor within a single frame.</p>
     */
    public static void updateDynamicDistanceScale(long deltaNanos) {
        if (deltaNanos > 20_000_000L) {
            dynamicDistanceScale = Math.max(0.35, dynamicDistanceScale - 0.05);
        } else if (deltaNanos < 14_000_000L && dynamicDistanceScale < 1.0) {
            dynamicDistanceScale = Math.min(1.0, dynamicDistanceScale + 0.02);
        }
    }

    /** Evaluates whether an entity should be culled using cached camera position. */
    public static boolean shouldCullEntity(Entity entity) {
        return shouldCullEntity(entity, cameraX, cameraY, cameraZ);
    }

    /** Evaluates whether an entity should be culled from rendering. */
    public static boolean shouldCullEntity(Entity entity, double camX, double camY, double camZ) {
        if (!enabled || entity == null) {
            return false;
        }

        // The Stutter Hunter needs to know how much of the frame went into
        // visibility decisions - a mob farm is the classic offender.
        long start = System.nanoTime();
        boolean culled = decideEntityCull(entity, camX, camY, camZ);
        dev.hyperionfps.telemetry.ThoriumPhaseTimer.addEntities(System.nanoTime() - start);
        return culled;
    }

    private static boolean decideEntityCull(Entity entity, double camX, double camY, double camZ) {
        AABB box = entity.getBoundingBox();

        // 1. Frustum Culling
        if (!FrustumCache.isVisible(box)) {
            return true;
        }

        // 2. Dynamic Distance Scaling
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double distSqr = dx * dx + dy * dy + dz * dz;

        double baseMaxDistance = extremeFpsMode ? 24.0 : 64.0;
        double effectiveMaxDistance = baseMaxDistance * dynamicDistanceScale;
        if (distSqr > (effectiveMaxDistance * effectiveMaxDistance)) {
            return true;
        }

        // 3. Hierarchical Spatial BVH Chunk Cluster Culling (O(1) cluster skip)
        if (ThoriumBvhCuller.isEntityClusterOccluded(entity, camX, camY, camZ, currentLevel)) {
            return true;
        }

        // 4. Section & Raycast Occlusion Check (only beyond 3.5 blocks)
        if (distSqr > 12.25 && currentLevel != null) {
            int secX = ((int) Math.floor(entity.getX())) >> 4;
            int secY = ((int) Math.floor(entity.getY())) >> 4;
            int secZ = ((int) Math.floor(entity.getZ())) >> 4;
            if (OcclusionGrid.isSectionOccluded(currentLevel, camX, camY, camZ, secX, secY, secZ)) {
                return true;
            }
            return OcclusionRaycaster.isOccluded(currentLevel, camX, camY, camZ,
                    entity.getX(), entity.getY() + (entity.getEyeHeight() * 0.5), entity.getZ());
        }

        return false;
    }

    /** Evaluates whether a block entity should be culled using cached camera position. */
    public static boolean shouldCullBlockEntity(BlockEntity blockEntity) {
        return shouldCullBlockEntity(blockEntity, cameraX, cameraY, cameraZ);
    }

    /** Evaluates whether a block entity (Chest, Sign, Hopper, Head) should be culled. */
    public static boolean shouldCullBlockEntity(BlockEntity blockEntity, double camX, double camY, double camZ) {
        if (!enabled || blockEntity == null) {
            return false;
        }

        long start = System.nanoTime();
        boolean culled = decideBlockEntityCull(blockEntity, camX, camY, camZ);
        dev.hyperionfps.telemetry.ThoriumPhaseTimer.addEntities(System.nanoTime() - start);
        return culled;
    }

    private static boolean decideBlockEntityCull(BlockEntity blockEntity, double camX, double camY, double camZ) {
        BlockPos pos = blockEntity.getBlockPos();
        double bx = pos.getX() + 0.5;
        double by = pos.getY() + 0.5;
        double bz = pos.getZ() + 0.5;

        // 1. Dynamic Distance Scaling. Checked before the frustum test on purpose: the
        // frustum test needs a temporary AABB, and this rejects the vast majority of the
        // block entities without allocating anything.
        double dx = bx - camX;
        double dy = by - camY;
        double dz = bz - camZ;
        double distSqr = dx * dx + dy * dy + dz * dz;

        double maxDist = (extremeFpsMode ? 16.0 : 48.0) * dynamicDistanceScale;
        if (distSqr > (maxDist * maxDist)) {
            return true;
        }

        // 2. Frustum Culling (test 1x1x1 block bounds)
        if (!FrustumCache.isVisible(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)) {
            return true;
        }

        // 3. Section & Raycast Occlusion Check for solid walls
        if (distSqr > 9.0 && currentLevel != null) {
            int secX = pos.getX() >> 4;
            int secY = pos.getY() >> 4;
            int secZ = pos.getZ() >> 4;
            if (OcclusionGrid.isSectionOccluded(currentLevel, camX, camY, camZ, secX, secY, secZ)) {
                return true;
            }
            long posKey = pos.asLong();
            Boolean cached = BLOCK_ENTITY_CULLED_CACHE.get(posKey);
            if (cached != null) {
                return cached;
            }
            boolean occluded = OcclusionRaycaster.isOccluded(currentLevel, camX, camY, camZ, bx, by, bz);
            BLOCK_ENTITY_CULLED_CACHE.put(posKey, occluded);
            return occluded;
        }

        return false;
    }

    /** Evaluates whether a particle should be culled before creation or during rendering. */
    public static boolean shouldCullParticle(double x, double y, double z) {
        if (!enabled) {
            return false;
        }

        int maxParticleBudget = extremeFpsMode
                ? ThoriumParticleBudget.EXTREME_BUDGET
                : ThoriumParticleBudget.DEFAULT_BUDGET;
        if (!ThoriumParticleBudget.admit(maxParticleBudget)) {
            return true;
        }

        // Frustum & Distance checks
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        double distSqr = dx * dx + dy * dy + dz * dz;

        double maxParticleDistSqr = extremeFpsMode ? 144.0 : 1024.0; // 12 blocks in extreme, 32 blocks in normal
        if (distSqr > maxParticleDistSqr) {
            return true;
        }

        // Cull particles behind solid blocks
        if (distSqr > 16.0 && currentLevel != null) {
            return OcclusionRaycaster.isOccluded(currentLevel, cameraX, cameraY, cameraZ, x, y, z);
        }

        return false;
    }

    public static boolean isExtremeFpsMode() {
        return extremeFpsMode;
    }

    public static void setExtremeFpsMode(boolean enabled) {
        extremeFpsMode = enabled;
        LOGGER.info("Extreme FPS Mode: {}", enabled ? "ENABLED" : "DISABLED");
    }

    public static double getDynamicDistanceScale() {
        return dynamicDistanceScale;
    }

    public static void setEnabled(boolean cullingEnabled) {
        enabled = cullingEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}

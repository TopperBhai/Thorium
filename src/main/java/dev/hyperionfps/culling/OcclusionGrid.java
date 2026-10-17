package dev.hyperionfps.culling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.world.level.BlockGetter;

/**
 * Coarse 16x16x16 subchunk (section) occlusion grid.
 *
 * <p>In cave systems, dungeons, and indoor bases, dozens of mobs and block entities
 * often reside inside completely hidden sections behind solid stone walls.
 * OcclusionGrid performs coarse raycast tests against section bounds; when an entire
 * 16x16x16 subchunk is occluded, all entities and block entities inside it are culled
 * in O(1) without requiring individual per-mob raycasts.</p>
 */
public final class OcclusionGrid {

    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    private static final ConcurrentHashMap<Long, Boolean> SECTION_CACHE = new ConcurrentHashMap<>(1024);

    private static final AtomicLong OCCLUSION_CHECKS = new AtomicLong(0);
    private static final AtomicLong SECTIONS_OCCLUDED_COUNT = new AtomicLong(0);

    private static volatile int lastPlayerSecX = Integer.MIN_VALUE;
    private static volatile int lastPlayerSecY = Integer.MIN_VALUE;
    private static volatile int lastPlayerSecZ = Integer.MIN_VALUE;

    private OcclusionGrid() {
    }

    /**
     * Packs section coordinates into a 64-bit key.
     */
    public static long packSectionPos(int secX, int secY, int secZ) {
        return (((long) (secX & 0x3FFFFF)) << 42)
                | (((long) (secY & 0xFFFFF)) << 20)
                | ((long) (secZ & 0x3FFFFF));
    }

    /**
     * Updates player camera position. If the player crosses into a new section,
     * the coarse section cache is invalidated.
     */
    public static void onCameraUpdate(double camX, double camY, double camZ) {
        int secX = ((int) Math.floor(camX)) >> 4;
        int secY = ((int) Math.floor(camY)) >> 4;
        int secZ = ((int) Math.floor(camZ)) >> 4;

        if (secX != lastPlayerSecX || secY != lastPlayerSecY || secZ != lastPlayerSecZ) {
            lastPlayerSecX = secX;
            lastPlayerSecY = secY;
            lastPlayerSecZ = secZ;
            invalidate();
        }
    }

    /**
     * Evaluates whether a 16x16x16 section is completely occluded from the camera.
     */
    public static boolean isSectionOccluded(BlockGetter level, double camX, double camY, double camZ,
                                           int secX, int secY, int secZ) {
        if (!ENABLED.get() || level == null) {
            return false;
        }

        // Camera section is never occluded
        int camSecX = ((int) Math.floor(camX)) >> 4;
        int camSecY = ((int) Math.floor(camY)) >> 4;
        int camSecZ = ((int) Math.floor(camZ)) >> 4;
        if (secX == camSecX && secY == camSecY && secZ == camSecZ) {
            return false;
        }

        long key = packSectionPos(secX, secY, secZ);
        Boolean cached = SECTION_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        OCCLUSION_CHECKS.incrementAndGet();

        // Test section center point
        double centerX = (secX << 4) + 8.0;
        double centerY = (secY << 4) + 8.0;
        double centerZ = (secZ << 4) + 8.0;

        boolean centerOccluded = OcclusionRaycaster.isOccluded(level, camX, camY, camZ, centerX, centerY, centerZ);
        if (!centerOccluded) {
            // If the center has line of sight, the section is visibly accessible
            SECTION_CACHE.put(key, false);
            return false;
        }

        // Test two opposite corner probe points to confirm complete section occlusion
        double minX = (secX << 4) + 1.0;
        double minY = (secY << 4) + 1.0;
        double minZ = (secZ << 4) + 1.0;
        boolean corner1Occluded = OcclusionRaycaster.isOccluded(level, camX, camY, camZ, minX, minY, minZ);

        double maxX = (secX << 4) + 15.0;
        double maxY = (secY << 4) + 15.0;
        double maxZ = (secZ << 4) + 15.0;
        boolean corner2Occluded = OcclusionRaycaster.isOccluded(level, camX, camY, camZ, maxX, maxY, maxZ);

        boolean fullyOccluded = corner1Occluded && corner2Occluded;
        SECTION_CACHE.put(key, fullyOccluded);
        if (fullyOccluded) {
            SECTIONS_OCCLUDED_COUNT.incrementAndGet();
        }
        return fullyOccluded;
    }

    /**
     * Clears cached section occlusion states.
     */
    public static void invalidate() {
        SECTION_CACHE.clear();
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
        if (!enabled) {
            invalidate();
        }
    }

    public static long getOcclusionChecks() {
        return OCCLUSION_CHECKS.get();
    }

    public static long getSectionsOccludedCount() {
        return SECTIONS_OCCLUDED_COUNT.get();
    }

    public static int getCacheSize() {
        return SECTION_CACHE.size();
    }
}

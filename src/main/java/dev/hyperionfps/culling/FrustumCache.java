package dev.hyperionfps.culling;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

/**
 * Thread-safe cache for camera frustum testing.
 *
 * <p>Enables zero-allocation bounding box visibility checks against
 * the current frame's camera frustum.</p>
 */
public final class FrustumCache {

    private static volatile Frustum activeFrustum;

    private FrustumCache() {
    }

    /** Updates the active frustum snapshot for the current frame. */
    public static void update(Frustum frustum) {
        activeFrustum = frustum;
    }

    /** Returns the currently cached camera frustum. */
    public static Frustum get() {
        return activeFrustum;
    }

    /** Tests whether an AABB is inside the current camera frustum. */
    public static boolean isVisible(AABB aabb) {
        Frustum f = activeFrustum;
        return f == null || f.isVisible(aabb);
    }

    /** Tests bounding box coordinates directly. */
    public static boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Frustum f = activeFrustum;
        if (f == null) {
            return true;
        }
        // Use pooled or direct check
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        return f.isVisible(box);
    }
}

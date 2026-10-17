package dev.hyperionfps.render.state;

import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance Java-side OpenGL & Pipeline state tracker.
 *
 * <p>Integrated Graphics (Intel UHD/Iris, AMD Radeon Vega/RDNA) and entry GPUs
 * spend substantial CPU time in driver JNI transitions for redundant state changes
 * (binding identical textures, rebinding identical shader programs, resetting
 * matching blend states). This tracker intercepts and skips redundant calls
 * before reaching native LWJGL/driver code.</p>
 */
public final class GlStateTracker {

    private static final int MAX_TEXTURE_UNITS = 32;
    private static final int[] BOUND_TEXTURE_2D = new int[MAX_TEXTURE_UNITS];
    private static int activeTextureUnit = 0;
    private static int currentProgram = 0;

    private static boolean blendEnabled = false;
    private static int blendSrc = 0;
    private static int blendDst = 0;

    private static boolean depthEnabled = true;
    private static boolean cullEnabled = true;

    // Metrics tracking
    private static final AtomicLong REDUNDANT_TEXTURE_BINDS_AVOIDED = new AtomicLong(0);
    private static final AtomicLong REDUNDANT_PROGRAMS_AVOIDED = new AtomicLong(0);
    private static final AtomicLong REDUNDANT_STATE_CHANGES_AVOIDED = new AtomicLong(0);

    private GlStateTracker() {
    }

    /**
     * Checks if the texture bind call is redundant.
     * @return {@code true} if the driver call must proceed; {@code false} if redundant and can be skipped.
     */
    public static boolean shouldBindTexture(int textureId) {
        if (activeTextureUnit >= 0 && activeTextureUnit < MAX_TEXTURE_UNITS) {
            if (BOUND_TEXTURE_2D[activeTextureUnit] == textureId) {
                REDUNDANT_TEXTURE_BINDS_AVOIDED.incrementAndGet();
                return false;
            }
            BOUND_TEXTURE_2D[activeTextureUnit] = textureId;
        }
        return true;
    }

    /**
     * Checks if the shader program switch is redundant.
     * @return {@code true} if driver call must proceed; {@code false} if redundant.
     */
    public static boolean shouldUseProgram(int programId) {
        if (currentProgram == programId) {
            REDUNDANT_PROGRAMS_AVOIDED.incrementAndGet();
            return false;
        }
        currentProgram = programId;
        return true;
    }

    /**
     * Checks if the blend enable/disable is redundant.
     */
    public static boolean shouldSetBlend(boolean enable) {
        if (blendEnabled == enable) {
            REDUNDANT_STATE_CHANGES_AVOIDED.incrementAndGet();
            return false;
        }
        blendEnabled = enable;
        return true;
    }

    /**
     * Checks if the blend function parameters are redundant.
     */
    public static boolean shouldSetBlendFunc(int src, int dst) {
        if (blendSrc == src && blendDst == dst) {
            REDUNDANT_STATE_CHANGES_AVOIDED.incrementAndGet();
            return false;
        }
        blendSrc = src;
        blendDst = dst;
        return true;
    }

    /**
     * Checks if the depth test state change is redundant.
     */
    public static boolean shouldSetDepth(boolean enable) {
        if (depthEnabled == enable) {
            REDUNDANT_STATE_CHANGES_AVOIDED.incrementAndGet();
            return false;
        }
        depthEnabled = enable;
        return true;
    }

    /**
     * Checks if face culling state change is redundant.
     */
    public static boolean shouldSetCull(boolean enable) {
        if (cullEnabled == enable) {
            REDUNDANT_STATE_CHANGES_AVOIDED.incrementAndGet();
            return false;
        }
        cullEnabled = enable;
        return true;
    }

    public static void setActiveTextureUnit(int unit) {
        if (unit >= 0 && unit < MAX_TEXTURE_UNITS) {
            activeTextureUnit = unit;
        }
    }

    public static long getRedundantTextureBindsAvoided() {
        return REDUNDANT_TEXTURE_BINDS_AVOIDED.get();
    }

    public static long getRedundantProgramsAvoided() {
        return REDUNDANT_PROGRAMS_AVOIDED.get();
    }

    public static long getRedundantStateChangesAvoided() {
        return REDUNDANT_STATE_CHANGES_AVOIDED.get();
    }
}

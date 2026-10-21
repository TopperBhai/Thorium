package dev.hyperionfps.frame;

import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.adaptive.RenderDistanceGovernor;
import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.FrustumCache;
import dev.hyperionfps.culling.OcclusionGrid;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.BlockGetter;

/**
 * The authoritative per-frame context of the client.
 *
 * <p>Before this class existed, the camera context was published from
 * {@code AsyncOcclusionCuller#onFrameBegin}, which the modern entity-culling mixin
 * called <em>once per entity</em>. Everything downstream of that call - the BVH cache
 * flush, the dynamic distance scale and the adaptive-distance EMA - therefore ran
 * hundreds of times per frame with hundreds of times too small a time delta, which
 * collapsed the distance scale to its floor within a single frame and fed the adaptive
 * controller a frame time of a few microseconds.</p>
 *
 * <p>Two frame boundaries are published here, each exactly once per frame:</p>
 *
 * <ul>
 *   <li><b>Client frame</b> - driven by the core {@code Minecraft#runTick} hook, which
 *       runs once per iteration of Minecraft's main loop. It feeds the frametime
 *       tracker and the frame pacer.</li>
 *   <li><b>World frame</b> - driven by the era-specific level-renderer hook, which runs
 *       once per rendered world frame. It publishes the camera position, the frustum and
 *       the level, and drives the culling governor.</li>
 * </ul>
 */
public final class ThoriumFrameContext {

    /**
     * How long the adaptive render-distance governor stays dormant after a world has
     * started rendering.
     *
     * <p>Loading a world is the single most expensive moment in a session: the client
     * streams in chunks, rebuilds the section meshes and stalls for hundreds of
     * milliseconds. The framerate during that window is low for a reason that has
     * nothing to do with the render distance, so the governor must not react to it -
     * a reaction here would cost a full chunk-cache reload right when the world is
     * still settling.</p>
     */
    public static final long ADAPTIVE_WARMUP_NANOS = 10_000_000_000L;

    private static final AtomicLong CLIENT_FRAMES = new AtomicLong();
    private static final AtomicLong WORLD_FRAMES = new AtomicLong();

    private static volatile long lastClientFrameNanos = System.nanoTime();
    private static volatile long lastWorldFrameNanos = System.nanoTime();
    private static volatile long lastClientDeltaNanos = 0L;
    private static volatile long lastWorldDeltaNanos = 0L;

    /** Timestamp of the first world frame of the current world. */
    private static volatile long firstWorldFrameNanos = 0L;

    /** The world the published camera context belongs to. */
    private static volatile BlockGetter currentLevel;

    private ThoriumFrameContext() {
    }

    /**
     * Opens a client frame: the main loop iteration that produced the previous frame
     * has just finished.
     *
     * @param worldLoaded whether a world is currently loaded; the frame pacer only
     *                    runs in a world, so the menus are never throttled
     */
    public static void beginClientFrame(boolean worldLoaded) {
        long now = System.nanoTime();
        lastClientDeltaNanos = now - lastClientFrameNanos;
        lastClientFrameNanos = now;
        CLIENT_FRAMES.incrementAndGet();

        // Record first, pace second: the recorded frametime then contains the pacing
        // sleep, which is what the player actually experiences.
        ThoriumFrametimeTracker.recordFrame();
        if (worldLoaded) {
            ThoriumFramePacer.paceFrame();
        }
    }

    /**
     * Opens a world frame and publishes the camera context.
     *
     * @param camX     camera X position
     * @param camY     camera Y position
     * @param camZ     camera Z position
     * @param frustum  the frame's culling frustum, or {@code null} when the engine did
     *                 not expose one for this frame
     * @param level    the level being rendered, or {@code null} outside a world
     */
    public static void beginWorldFrame(double camX, double camY, double camZ,
                                       Frustum frustum, BlockGetter level) {
        long now = System.nanoTime();
        long deltaNanos = now - lastWorldFrameNanos;
        lastWorldFrameNanos = now;
        lastWorldDeltaNanos = deltaNanos;
        WORLD_FRAMES.incrementAndGet();

        // A different world (or a world that was left and re-entered) restarts the
        // warm-up window: the first seconds of a new world are the most expensive
        // ones and the least representative. A null level (no world at all) must not
        // reset it, otherwise the window could never expire.
        if (level != null && level != currentLevel) {
            currentLevel = level;
            firstWorldFrameNanos = now;
        }

        AsyncOcclusionCuller.publishCamera(camX, camY, camZ, level);
        OcclusionGrid.onCameraUpdate(camX, camY, camZ);
        if (frustum != null) {
            FrustumCache.update(frustum);
        }

        // The BVH cluster cache is cleared once per frame, never once per entity.
        ThoriumBvhCuller.onFrameBegin();

        // The distance governor is driven by a real frame delta, once per frame.
        AsyncOcclusionCuller.updateDynamicDistanceScale(deltaNanos);
        AdaptiveDistanceController.getInstance().onFrame(deltaNanos);
        RenderDistanceGovernor.pollUserOverride();
        if (firstWorldFrameNanos != 0L
                && now - firstWorldFrameNanos >= ADAPTIVE_WARMUP_NANOS
                && !RenderDistanceGovernor.isUserOverrideActive()) {
            AdaptiveDistanceController.getInstance().checkAndAdjust(System.currentTimeMillis());
        }

        ThoriumEntityLodThrottler.onFrame();
    }

    /** Number of client frames observed so far. */
    public static long clientFrameCount() {
        return CLIENT_FRAMES.get();
    }

    /** Number of world frames observed so far. */
    public static long worldFrameCount() {
        return WORLD_FRAMES.get();
    }

    /** Duration of the last client frame in nanoseconds. */
    public static long lastClientDeltaNanos() {
        return lastClientDeltaNanos;
    }

    /** Duration of the last world frame in nanoseconds. */
    public static long lastWorldDeltaNanos() {
        return lastWorldDeltaNanos;
    }
}

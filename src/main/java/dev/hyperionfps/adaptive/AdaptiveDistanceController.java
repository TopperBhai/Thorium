package dev.hyperionfps.adaptive;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptive Render Distance Governor with Exponential Moving Average (EMA) smoothing.
 *
 * <p>A fixed render distance causes stuttering and FPS drops on budget/integrated GPUs
 * in dense areas (villages, jungles, complex bases) while under-utilizing hardware in
 * open terrain. The AdaptiveDistanceController dynamically scales chunk render distance
 * down in real time to hold a smooth target framerate (e.g., 60 FPS).</p>
 *
 * <h2>Why the governor only ever lowers the distance</h2>
 *
 * <p>Writing {@code Options#renderDistance} at runtime is <b>not</b> a cosmetic change.
 * On a single-player world the integrated server polls that very option every server
 * tick and mirrors it into {@code PlayerList#setViewDistance}, which broadcasts a
 * {@code ClientboundSetChunkCacheRadiusPacket} to the client. The client answers that
 * packet with {@code ClientChunkCache#updateViewRadius}, and that method allocates a
 * brand-new {@code ClientChunkCache.Storage} - throwing away <b>every</b> cached chunk,
 * not just the ones that fell outside the new radius. The whole visible world is then
 * re-downloaded and re-meshed.</p>
 *
 * <p>One single +/-1 chunk step therefore costs a full chunk reload and a multi-frame
 * hitch. An earlier version of this class raised the distance whenever the smoothed
 * framerate was more than 10 FPS above the target, which is the normal state on any
 * capable machine - so it ratcheted the distance up to the maximum every two seconds
 * and reloaded the entire world every two seconds for as long as the player was in the
 * world.</p>
 *
 * <p>The governor therefore behaves as a load-shedding mechanism:</p>
 * <ul>
 *   <li>It <b>lowers</b> the distance when the framerate is genuinely below target.</li>
 *   <li>It <b>never raises</b> it on its own. Asking the server for more chunks than it
 *       is sending is pointless, and raising the distance is the player's decision -
 *       when the player changes it, {@link RenderDistanceGovernor#pollUserOverride()}
 *       re-syncs this controller and makes it yield.</li>
 *   <li>Every change is rate-limited by a settle window so that a single stutter can
 *       never turn into a reload loop.</li>
 * </ul>
 */
public final class AdaptiveDistanceController {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/AdaptiveDistance");
    private static final AdaptiveDistanceController INSTANCE = new AdaptiveDistanceController();

    public static final int DEFAULT_TARGET_FPS = 60;
    public static final int DEFAULT_MIN_DISTANCE = 4;
    public static final int DEFAULT_MAX_DISTANCE = 32;

    /** How often the governor is allowed to evaluate the current framerate. */
    public static final long DEFAULT_ADJUST_INTERVAL_MS = 2000L;

    /**
     * Minimum time between two <em>applied</em> changes.
     *
     * <p>A distance change reloads the whole chunk cache, so the framerate needs time to
     * recover before the next decision is made. Without this window the governor would
     * re-evaluate two seconds after every change and reload the world again.</p>
     */
    public static final long DEFAULT_SETTLE_INTERVAL_MS = 30_000L;

    /** Consecutive low-framerate evaluations required before a step down. */
    public static final int REQUIRED_LOW_SAMPLES = 2;

    /** The smoothed framerate must be this far below the target to count as "low". */
    public static final double LOW_FPS_MARGIN = 5.0;

    /** EMA smoothing factor. */
    public static final double DEFAULT_ALPHA = 0.15;

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    private volatile int targetFps = DEFAULT_TARGET_FPS;
    private volatile int minDistance = DEFAULT_MIN_DISTANCE;
    private volatile int maxDistance = DEFAULT_MAX_DISTANCE;
    private volatile int currentDistance = 12;

    private volatile double smoothedFps = DEFAULT_TARGET_FPS;
    private volatile long lastEvaluationTimeMs = System.currentTimeMillis();
    private volatile long lastChangeTimeMs = 0L;
    private volatile int lowFpsSamples = 0;

    private final AtomicInteger adjustmentsMade = new AtomicInteger(0);
    private volatile IntConsumer distanceConsumer;

    private AdaptiveDistanceController() {
    }

    public static AdaptiveDistanceController getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a consumer (e.g. Minecraft GameOptions updater) invoked when distance changes.
     */
    public void setDistanceConsumer(IntConsumer consumer) {
        this.distanceConsumer = consumer;
    }

    /**
     * Records a newly rendered frame's delta time in nanoseconds and updates the smoothed FPS.
     */
    public void onFrame(long deltaNanos) {
        if (!enabled.get() || deltaNanos <= 0) {
            return;
        }

        double instantFps = 1_000_000_000.0 / deltaNanos;
        // Clamp extreme outliers (e.g. pause menus or window resize pauses)
        instantFps = Math.max(1.0, Math.min(1000.0, instantFps));

        // Exponential Moving Average (EMA)
        smoothedFps = (DEFAULT_ALPHA * instantFps) + ((1.0 - DEFAULT_ALPHA) * smoothedFps);
    }

    /**
     * Evaluates whether the render distance has to be lowered to hold the target
     * framerate. Call once per world frame.
     *
     * <p>The distance is never raised from here - see the class documentation for why.
     * A change only happens when the framerate has been below target for
     * {@link #REQUIRED_LOW_SAMPLES} consecutive evaluations <em>and</em> the settle
     * window since the last change has elapsed.</p>
     *
     * @param currentTimeMs current wall-clock time in milliseconds
     * @return {@code true} when a new distance was applied
     */
    public boolean checkAndAdjust(long currentTimeMs) {
        if (!enabled.get()) {
            return false;
        }

        // 1. Only look at the framerate once per evaluation interval.
        if (currentTimeMs - lastEvaluationTimeMs < DEFAULT_ADJUST_INTERVAL_MS) {
            return false;
        }
        lastEvaluationTimeMs = currentTimeMs;

        // 2. The world needs time to settle after a reload before the next decision.
        if (currentTimeMs - lastChangeTimeMs < DEFAULT_SETTLE_INTERVAL_MS) {
            lowFpsSamples = 0;
            return false;
        }

        boolean low = smoothedFps < (targetFps - LOW_FPS_MARGIN);
        lowFpsSamples = low ? lowFpsSamples + 1 : 0;

        if (lowFpsSamples < REQUIRED_LOW_SAMPLES) {
            return false;
        }

        int distance = currentDistance;
        if (distance <= minDistance) {
            return false;
        }

        int next = distance - 1;
        currentDistance = next;
        lastChangeTimeMs = currentTimeMs;
        lowFpsSamples = 0;
        adjustmentsMade.incrementAndGet();
        LOGGER.info("Adaptive Distance stepped DOWN to {} chunks (Smoothed FPS: {})", next, (int) smoothedFps);

        if (distanceConsumer != null) {
            distanceConsumer.accept(next);
        }

        return true;
    }

    public int getTargetFps() {
        return targetFps;
    }

    public void setTargetFps(int fps) {
        this.targetFps = Math.max(20, Math.min(360, fps));
    }

    public int getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(int distance) {
        this.minDistance = Math.max(2, Math.min(this.maxDistance, distance));
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(int distance) {
        this.maxDistance = Math.max(this.minDistance, Math.min(64, distance));
    }

    public int getCurrentDistance() {
        return currentDistance;
    }

    public void setCurrentDistance(int distance) {
        this.currentDistance = Math.max(minDistance, Math.min(maxDistance, distance));
    }

    public double getSmoothedFps() {
        return smoothedFps;
    }

    public void setSmoothedFps(double fps) {
        this.smoothedFps = fps;
    }

    public int getAdjustmentsMade() {
        return adjustmentsMade.get();
    }

    /** Timestamp of the last applied distance change, or {@code 0} if there was none. */
    public long getLastChangeTimeMs() {
        return lastChangeTimeMs;
    }

    /** Number of consecutive low-framerate evaluations seen so far. */
    public int getLowFpsSamples() {
        return lowFpsSamples;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
        if (!value) {
            this.lowFpsSamples = 0;
        }
    }

    public void reset() {
        this.smoothedFps = targetFps;
        this.adjustmentsMade.set(0);
        this.lowFpsSamples = 0;
        this.lastEvaluationTimeMs = System.currentTimeMillis();
        this.lastChangeTimeMs = 0L;
    }
}

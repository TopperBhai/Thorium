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
 * up or down in real time to lock onto a smooth target framerate (e.g., 60 FPS)
 * without abrupt visual hitching.</p>
 */
public final class AdaptiveDistanceController {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/AdaptiveDistance");
    private static final AdaptiveDistanceController INSTANCE = new AdaptiveDistanceController();

    public static final int DEFAULT_TARGET_FPS = 60;
    public static final int DEFAULT_MIN_DISTANCE = 4;
    public static final int DEFAULT_MAX_DISTANCE = 32;
    public static final long DEFAULT_ADJUST_INTERVAL_MS = 2000L; // 2 seconds between steps
    public static final double DEFAULT_ALPHA = 0.15; // EMA smoothing factor

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private volatile int targetFps = DEFAULT_TARGET_FPS;
    private volatile int minDistance = DEFAULT_MIN_DISTANCE;
    private volatile int maxDistance = DEFAULT_MAX_DISTANCE;
    private volatile int currentDistance = 12;

    private volatile double smoothedFps = DEFAULT_TARGET_FPS;
    private volatile long lastAdjustTimeMs = System.currentTimeMillis();

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
     * Evaluates whether render distance needs to be adjusted. Call once per tick or frame.
     */
    public boolean checkAndAdjust(long currentTimeMs) {
        if (!enabled.get()) {
            return false;
        }

        if (currentTimeMs - lastAdjustTimeMs < DEFAULT_ADJUST_INTERVAL_MS) {
            return false; // In adjustment cooldown
        }

        int target = this.targetFps;
        int distance = this.currentDistance;
        boolean changed = false;

        // If FPS is below target by > 10%, drop distance by 1 chunk to recover framerate
        if (smoothedFps < (target - 5.0) && distance > minDistance) {
            currentDistance = distance - 1;
            changed = true;
            lastAdjustTimeMs = currentTimeMs;
            adjustmentsMade.incrementAndGet();
            LOGGER.debug("Adaptive Distance stepped DOWN to {} chunks (Smoothed FPS: {})", currentDistance, (int) smoothedFps);
        }
        // If FPS has > 15% headroom above target, raise distance by 1 chunk
        else if (smoothedFps > (target + 10.0) && distance < maxDistance) {
            currentDistance = distance + 1;
            changed = true;
            lastAdjustTimeMs = currentTimeMs;
            adjustmentsMade.incrementAndGet();
            LOGGER.debug("Adaptive Distance stepped UP to {} chunks (Smoothed FPS: {})", currentDistance, (int) smoothedFps);
        }

        if (changed && distanceConsumer != null) {
            distanceConsumer.accept(currentDistance);
        }

        return changed;
    }

    public int getTargetFps() {
        return targetFps;
    }

    public void setTargetFps(int targetFps) {
        this.targetFps = Math.max(20, Math.min(360, targetFps));
    }

    public int getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(int minDistance) {
        this.minDistance = Math.max(2, Math.min(this.maxDistance, minDistance));
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(int maxDistance) {
        this.maxDistance = Math.max(this.minDistance, Math.min(64, maxDistance));
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

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
    }

    public void reset() {
        this.smoothedFps = targetFps;
        this.adjustmentsMade.set(0);
        this.lastAdjustTimeMs = System.currentTimeMillis();
    }
}

package dev.hyperionfps.frame;

/**
 * High-performance, zero-allocation circular frametime and micro-stutter tracker.
 *
 * <p>Records frame deltas with sub-millisecond precision using a fixed circular buffer.
 * Dynamically computes frame jitter, detects micro-stutter spikes, and calculates
 * the real-time Smoothness Stability Index without allocating any objects on the render thread.</p>
 */
public final class ThoriumFrametimeTracker {

    /** Number of history samples displayed on the graph (power of 2 for fast bitmasking). */
    public static final int SAMPLE_COUNT = 128;
    private static final int INDEX_MASK = SAMPLE_COUNT - 1;

    private static final float[] FRAME_DURATIONS_MS = new float[SAMPLE_COUNT];
    private static final boolean[] FRAME_IS_STUTTER = new boolean[SAMPLE_COUNT];

    private static int headIndex = 0;
    private static long lastTimestampNanos = System.nanoTime();

    private static float currentFrametimeMs = 0.0f;
    private static float minFrametimeMs = 1000.0f;
    private static float maxFrametimeMs = 0.0f;
    private static float averageFrametimeMs = 0.0f;

    private static int totalStutterSpikes = 0;
    private static float smoothnessScore = 100.0f;

    /** Whether the most recently recorded frame was classified as a stutter. */
    private static volatile boolean lastFrameWasStutter;

    private ThoriumFrametimeTracker() {
    }

    /**
     * Records the completion of a rendered frame.
     * Must be called once per frame from the main render thread.
     */
    public static void recordFrame() {
        long now = System.nanoTime();
        long deltaNanos = now - lastTimestampNanos;
        lastTimestampNanos = now;

        // Ignore abnormal pauses (such as window minimization or loading screen)
        if (deltaNanos <= 0 || deltaNanos > 500_000_000L) {
            lastFrameWasStutter = false;
            return;
        }

        float deltaMs = deltaNanos / 1_000_000.0f;
        currentFrametimeMs = deltaMs;

        int prevIndex = (headIndex - 1) & INDEX_MASK;
        float prevMs = FRAME_DURATIONS_MS[prevIndex];
        float jitter = Math.abs(deltaMs - prevMs);

        // A true stutter spike is a perceptible hitch: frametime > 33.3ms (< 30 FPS)
        // or a sudden frame delivery delay exceeding 2.5x the rolling average
        float threshold = Math.max(30.0f, averageFrametimeMs * 2.5f);
        boolean isStutter = deltaMs > threshold && jitter > 12.0f;

        FRAME_DURATIONS_MS[headIndex] = deltaMs;
        FRAME_IS_STUTTER[headIndex] = isStutter;
        headIndex = (headIndex + 1) & INDEX_MASK;
        lastFrameWasStutter = isStutter;

        // Update statistics across current window
        float sum = 0.0f;
        float min = 999.0f;
        float max = 0.0f;
        int stuttersInWindow = 0;

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            float val = FRAME_DURATIONS_MS[i];
            if (val > 0.0f) {
                sum += val;
                if (val < min) min = val;
                if (val > max) max = val;
                if (FRAME_IS_STUTTER[i]) {
                    stuttersInWindow++;
                }
            }
        }

        averageFrametimeMs = sum / SAMPLE_COUNT;
        minFrametimeMs = min;
        maxFrametimeMs = max;
        totalStutterSpikes = stuttersInWindow;

        // Smoothness score: percentage of butter-smooth frames in the window
        float ratio = 1.0f - ((float) stuttersInWindow / SAMPLE_COUNT);
        smoothnessScore = Math.max(0.0f, Math.min(100.0f, ratio * 100.0f));
    }

    /**
     * Restarts the frame clock without recording a sample.
     *
     * <p>Used while an intentional throttle (the AFK power saver) holds the client
     * at a low framerate: the measurement is suspended so that the statistics keep
     * describing the frames the player actually experienced, and the first frame
     * after the throttle ends is not mistaken for a 90 ms stutter.</p>
     */
    public static void resetClock() {
        lastTimestampNanos = System.nanoTime();
    }

    /**
     * Gets a historical frametime sample relative to the most recent frame.
     *
     * @param offsetFromLatest 0 is the most recent frame, (SAMPLE_COUNT - 1) is oldest
     * @return frametime in milliseconds
     */
    public static float getSampleMs(int offsetFromLatest) {
        int index = (headIndex - 1 - offsetFromLatest) & INDEX_MASK;
        return FRAME_DURATIONS_MS[index];
    }

    /**
     * Checks if a historical sample was flagged as a stutter spike.
     */
    public static boolean isSampleStutter(int offsetFromLatest) {
        int index = (headIndex - 1 - offsetFromLatest) & INDEX_MASK;
        return FRAME_IS_STUTTER[index];
    }

    public static float getCurrentFrametimeMs() {
        return currentFrametimeMs;
    }

    public static float getAverageFrametimeMs() {
        return averageFrametimeMs;
    }

    public static float getMinFrametimeMs() {
        return minFrametimeMs;
    }

    public static float getMaxFrametimeMs() {
        return maxFrametimeMs;
    }

    public static int getTotalStutterSpikes() {
        return totalStutterSpikes;
    }

    public static float getSmoothnessScore() {
        return smoothnessScore;
    }

    /**
     * Whether the most recently recorded frame was classified as a stutter.
     *
     * <p>Used by the Stutter Hunter, which has to agree with the tracker about what
     * a stutter is - two different definitions would produce two different
     * opinions about the same frame.</p>
     */
    public static boolean wasLastFrameStutter() {
        return lastFrameWasStutter;
    }
}

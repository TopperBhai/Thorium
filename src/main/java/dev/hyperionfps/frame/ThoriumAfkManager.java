package dev.hyperionfps.frame;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import dev.hyperionfps.core.RenderThreads;
import dev.hyperionfps.core.ThoriumFeatureToggles;

/**
 * AFK Battery &amp; Power Saver (Stealth AFK).
 *
 * <p>A Minecraft client that is left running on a desk burns a full core and drives
 * the GPU at 100 % for a picture nobody is looking at. When the player alt-tabs away
 * or simply stops touching the keyboard, Thorium drops the client to roughly ten
 * frames per second and parks the render thread for the rest of every frame. The
 * moment the player comes back, the park is cut short and the game responds again.
 *
 * <h2>What counts as activity</h2>
 * <ul>
 *   <li>A mouse movement, a mouse button press or a key press - reported by the
 *       input mixins.</li>
 *   <li>The window regaining focus - polled once per client frame.</li>
 * </ul>
 *
 * <h2>Why the park is sliced</h2>
 * <p>GLFW delivers its events on the render thread, and the render thread is the one
 * that parks. A single 90 ms park would therefore delay the reaction to the first
 * click after an AFK period by up to 90 ms. The park budget is therefore spent in
 * slices of at most {@value #ECO_PARK_SLICE_NANOS} ms: the CPU cost is identical, but
 * an input event is noticed within a single slice instead of after the whole park.</p>
 *
 * <p>Everything in here is plain volatile state and atomics - reading a flag never
 * blocks and never allocates (NFR-1, NFR-2). The clock is {@link System#nanoTime()},
 * never wall-clock time (NFR-6).</p>
 */
public final class ThoriumAfkManager {

    /** The framerate the client is throttled to while the player is away. */
    public static final int ECO_TARGET_FPS = 10;

    /** The nominal length of one eco frame, in nanoseconds. */
    public static final long ECO_FRAME_TIME_NANOS = 100_000_000L;

    /** How much of an eco frame is spent parked, in nanoseconds. */
    public static final long ECO_PARK_NANOS = 90_000_000L;

    /** The longest a single park slice may last, in nanoseconds. */
    public static final long ECO_PARK_SLICE_NANOS = 16_000_000L;

    private static final AtomicLong ECO_ENGAGEMENTS = new AtomicLong();
    private static final AtomicLong WAKE_COUNT = new AtomicLong();
    private static final AtomicLong PARK_COUNT = new AtomicLong();

    /** Timestamp of the last observed player input, in nanoseconds. */
    private static volatile long lastActivityNanos = System.nanoTime();

    /** Whether the game window currently has the focus. */
    private static volatile boolean windowFocused = true;

    /** Whether the client is currently throttled down. */
    private static volatile boolean ecoActive;

    /** How long the player has been idle, in milliseconds. */
    private static volatile long idleMillis;

    private ThoriumAfkManager() {
    }

    /**
     * Announces that the AFK power saver is live in this session.
     *
     * <p>Called once during client initialisation. It flips the feature from
     * "not available" to a working switch in the settings console - the console must
     * never offer a switch that cannot do anything (REQ-UI-2).</p>
     */
    public static void install() {
        ThoriumFeatureToggles.Feature.AFK_ECO.markWired();
    }

    // --------------------------------------------------------------------
    // Input reporting
    // --------------------------------------------------------------------

    /**
     * Reports that the player did something.
     *
     * <p>Called from the mouse and keyboard hooks. It stamps the activity clock and
     * lifts the eco throttle immediately, so a click is never swallowed by a
     * throttled frame.</p>
     */
    public static void onActivity() {
        lastActivityNanos = System.nanoTime();
        if (ecoActive) {
            ecoActive = false;
            WAKE_COUNT.incrementAndGet();
            unparkRenderThread();
        }
    }

    /**
     * Parks the calling thread for the eco budget.
     *
     * <p>Does nothing unless the eco throttle is active. The budget is spent in
     * slices so that a queued input event is picked up within one slice.</p>
     */
    public static void parkEcoFrame() {
        if (!ecoActive) {
            return;
        }
        PARK_COUNT.incrementAndGet();
        long deadline = System.nanoTime() + ECO_PARK_NANOS;
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            long slice = Math.min(remaining, ECO_PARK_SLICE_NANOS);
            LockSupport.parkNanos(slice);
            if (!ecoActive) {
                // Woken by an input event - do not spend the rest of the budget.
                return;
            }
        }
    }

    // --------------------------------------------------------------------
    // Per-frame update
    // --------------------------------------------------------------------

    /**
     * Recomputes the eco state for the current frame.
     *
     * @param nowNanos the current monotonic timestamp
     * @param focused  whether the game window has the focus
     */
    public static void update(long nowNanos, boolean focused) {
        if (focused != windowFocused) {
            windowFocused = focused;
            if (focused) {
                // Focus regained: treat it as activity so the throttle lifts at once.
                onActivity();
            }
        }

        if (!ThoriumFeatureToggles.isAfkEcoEnabled()) {
            if (ecoActive) {
                ecoActive = false;
                WAKE_COUNT.incrementAndGet();
            }
            idleMillis = 0L;
            return;
        }

        long idleNanos = nowNanos - lastActivityNanos;
        idleMillis = idleNanos <= 0L ? 0L : idleNanos / 1_000_000L;

        long timeoutMillis = ThoriumFeatureToggles.getAfkTimeoutSeconds() * 1000L;
        boolean shouldEco = !focused || idleMillis >= timeoutMillis;

        if (shouldEco) {
            if (!ecoActive) {
                ecoActive = true;
                ECO_ENGAGEMENTS.incrementAndGet();
            }
        } else if (ecoActive) {
            ecoActive = false;
            WAKE_COUNT.incrementAndGet();
        }
    }

    // --------------------------------------------------------------------
    // State
    // --------------------------------------------------------------------

    /** Whether the client is currently throttled down to the eco framerate. */
    public static boolean isEcoActive() {
        return ecoActive;
    }

    /** Whether the game window currently has the focus. */
    public static boolean isWindowFocused() {
        return windowFocused;
    }

    /** How long the player has been idle, in milliseconds. */
    public static long getIdleMillis() {
        return idleMillis;
    }

    /** How often the eco throttle has engaged in this session. */
    public static long getEcoEngagements() {
        return ECO_ENGAGEMENTS.get();
    }

    /** How often the eco throttle has been lifted again. */
    public static long getWakeCount() {
        return WAKE_COUNT.get();
    }

    /** How many frames have been parked by the eco throttle. */
    public static long getParkCount() {
        return PARK_COUNT.get();
    }

    /** The framerate the eco throttle aims for. */
    public static int getEcoTargetFps() {
        return ECO_TARGET_FPS;
    }

    /**
     * A short badge for the HUD, or an empty string when the eco throttle is idle.
     *
     * <p>Kept in the shared source so that every per-version HUD renderer shows the
     * same indicator.</p>
     */
    public static String getEcoBadge() {
        if (!ecoActive) {
            return "";
        }
        return " §8• §7Eco: §a" + ECO_TARGET_FPS + " FPS";
    }

    // --------------------------------------------------------------------
    // Internals
    // --------------------------------------------------------------------

    private static void unparkRenderThread() {
        Thread client = RenderThreads.clientThread();
        if (client != null) {
            LockSupport.unpark(client);
        }
    }

    /**
     * Restores the compiled defaults.
     *
     * <p>Test seam: the manager is process-wide, so a test that engages the throttle
     * has to be able to put it back.</p>
     */
    public static void reset() {
        lastActivityNanos = System.nanoTime();
        windowFocused = true;
        ecoActive = false;
        idleMillis = 0L;
        ECO_ENGAGEMENTS.set(0L);
        WAKE_COUNT.set(0L);
        PARK_COUNT.set(0L);
    }
}

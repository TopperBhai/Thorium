package dev.hyperionfps.input;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hyperionfps.core.ThoriumFeatureToggles;

/**
 * Ultra Low-Latency Reflex - late input sampling (REQ-HEAD-7).
 *
 * <h2>What the game does without this feature</h2>
 * <p>Minecraft drains the platform event queue at <em>one</em> point of the frame and
 * then applies the accumulated mouse movement to the camera a moment later. On every
 * supported version the drain happens <em>before</em> the client tick, and the camera
 * is built <em>after</em> it:</p>
 *
 * <pre>
 *   1.21.x :  poll &#8594; tick &#8594; handleAccumulatedMovement &#8594; render &#8594; [swap] &#8594; poll
 *   26.x   :  poll &#8594; tick &#8594; handleAccumulatedMovement &#8594; render &#8594; [swap]
 * </pre>
 *
 * <p>Everything the player does <em>during</em> the tick and the render therefore has
 * to wait for the next drain, and is applied to the camera one frame later than it
 * could have been. On 1.21.x the second drain sits behind the buffer swap, so the
 * movement is applied a whole frame late; on the 26.x nodes the drain sits at the top
 * of the loop, so the movement waits for the whole frame.</p>
 *
 * <h2>What this feature does</h2>
 * <p>It drains the event queue a second time, immediately before the game applies the
 * pending mouse movement - that is, at the tail of the frame, right before the camera
 * matrix is built. Movement that arrived while the frame was working is therefore
 * applied to <em>the frame the player is looking at</em> instead of the next one.</p>
 *
 * <p>The latency this removes is bounded by the work the frame still had to do after
 * the drain - the render. It is <em>not</em> a fixed number of milliseconds: the
 * class therefore measures what it actually did instead of advertising a constant
 * (see {@link #getRecoveredPixels()} and {@link #getFreshInputFrames()}).</p>
 *
 * <h2>What it does not do</h2>
 * <ul>
 *   <li>It does not touch the tick logic, so the movement packets the client sends to
 *       the server are exactly the ones vanilla would have sent.</li>
 *   <li>It does not touch the camera rotation itself: the game still applies the
 *       movement, with its own smoothing and its own sensitivity. Thorium only makes
 *       sure the movement it applies is the freshest one available.</li>
 *   <li>It does not re-implement the input pipeline. The platform queue is drained by
 *       the game's own routine, so the events are delivered exactly once - just
 *       earlier.</li>
 * </ul>
 *
 * <p>This class holds no Minecraft type at all, so the whole decision table is
 * unit-testable without a running game (AGENTS.md §9).</p>
 */
public final class ThoriumReflexController {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/Reflex");

    /**
     * The class that drains the platform event queue on this node.
     *
     * <p>It lives in the version-specific source tree under this exact name on every
     * supported Minecraft version; the shared controller finds it by name so that the
     * shared source never has to mention GLFW or SDL.</p>
     */
    private static final String PUMP_CLASS = "dev.hyperionfps.input.ReflexEventPumpImpl";

    /**
     * A mouse movement of at most this many pixels counts as "no movement".
     *
     * <p>GLFW reports the cursor position in sub-pixel units, so a mouse that has not
     * moved can still produce a fractional delta. Anything below a hundredth of a
     * pixel is noise, not input.</p>
     */
    public static final double FRESH_INPUT_EPSILON = 0.01;

    /** How many pixels one unit of the recovered-pixel counter represents. */
    private static final long MICRO_PIXELS = 1000L;

    private static final AtomicLong LATE_SAMPLES = new AtomicLong();
    private static final AtomicLong FRESH_INPUT_FRAMES = new AtomicLong();
    private static final AtomicLong RECOVERED_MICRO_PIXELS = new AtomicLong();

    /** The last mouse movement the late sample recovered, in pixels. */
    private static volatile double lastRecoveredPixels;

    /** The platform-specific drain, or {@link ReflexEventPump#NONE}. */
    private static volatile ReflexEventPump pump = ReflexEventPump.NONE;

    private ThoriumReflexController() {
    }

    // --------------------------------------------------------------------
    // Installation
    // --------------------------------------------------------------------

    /**
     * Brings the late input sampler online.
     *
     * <p>Called once during client initialisation. The platform drain is looked up by
     * name, so the shared source never mentions GLFW or SDL; a node whose drain cannot
     * be found simply stays unwired, and the settings console says
     * {@code NOT AVAILABLE} instead of pretending the feature works (REQ-UI-2).</p>
     */
    public static void install() {
        try {
            Class<?> impl = Class.forName(PUMP_CLASS);
            Object instance = impl.getDeclaredConstructor().newInstance();
            if (instance instanceof ReflexEventPump candidate) {
                installPump(candidate);
                ThoriumFeatureToggles.Feature.REFLEX.markWired();
                LOGGER.info("Ultra Low-Latency Reflex online: {}", impl.getName());
            } else {
                LOGGER.warn("{} does not implement {} - the late input sampler stays off",
                        impl.getName(), ReflexEventPump.class.getName());
            }
        } catch (Throwable failure) {
            LOGGER.warn("Ultra Low-Latency Reflex could not be installed: {}", failure.toString());
        }
    }

    /**
     * Installs a platform drain directly.
     *
     * <p>Public so that the version-specific implementations can register themselves
     * and so that the unit tests can substitute a fake drain.</p>
     *
     * @param candidate the drain to use; {@code null} disables the feature
     */
    public static void installPump(ReflexEventPump candidate) {
        pump = candidate == null ? ReflexEventPump.NONE : candidate;
    }

    /** Whether a platform drain is available on this node. */
    public static boolean isPumpInstalled() {
        return pump != ReflexEventPump.NONE;
    }

    // --------------------------------------------------------------------
    // The decision
    // --------------------------------------------------------------------

    /**
     * The whole decision table of the feature.
     *
     * <p>Three conditions, all of which have to hold:</p>
     * <ol>
     *   <li>the player has switched the feature on,</li>
     *   <li>this node can drain its platform event queue at all,</li>
     *   <li>and the game window has the focus - an unfocused window has no mouse
     *       movement to recover.</li>
     * </ol>
     *
     * @param featureEnabled whether the player switched the feature on
     * @param pumpInstalled  whether this node can drain its event queue
     * @param windowFocused  whether the game window currently has the focus
     * @return {@code true} when the late sample may run
     */
    static boolean decide(boolean featureEnabled, boolean pumpInstalled, boolean windowFocused) {
        return featureEnabled && pumpInstalled && windowFocused;
    }

    /**
     * Whether the late sample may run this frame.
     *
     * @param windowFocused whether the game window currently has the focus
     * @return {@code true} when the late sample may run
     */
    public static boolean shouldSample(boolean windowFocused) {
        return decide(ThoriumFeatureToggles.isReflexEnabled(), isPumpInstalled(), windowFocused);
    }

    // --------------------------------------------------------------------
    // The sample
    // --------------------------------------------------------------------

    /**
     * Drains the platform event queue.
     *
     * <p>Never throws: a platform that refuses to drain must not take the frame down
     * with it.</p>
     *
     * @return {@code true} when the queue was drained
     */
    public static boolean pumpEvents() {
        ReflexEventPump current = pump;
        if (current == ReflexEventPump.NONE) {
            return false;
        }
        try {
            current.pump();
            return true;
        } catch (Throwable failure) {
            LOGGER.debug("Late input sample failed: {}", failure.toString());
            return false;
        }
    }

    /**
     * Records what the late sample recovered.
     *
     * @param recoveredX the horizontal mouse movement the drain delivered
     * @param recoveredY the vertical mouse movement the drain delivered
     */
    public static void recordLateSample(double recoveredX, double recoveredY) {
        LATE_SAMPLES.incrementAndGet();
        double magnitude = Math.abs(recoveredX) + Math.abs(recoveredY);
        if (magnitude <= FRESH_INPUT_EPSILON) {
            return;
        }
        FRESH_INPUT_FRAMES.incrementAndGet();
        RECOVERED_MICRO_PIXELS.addAndGet(Math.round(magnitude * MICRO_PIXELS));
        lastRecoveredPixels = magnitude;
    }

    // --------------------------------------------------------------------
    // Metrics
    // --------------------------------------------------------------------

    /** How many frames were given a late input sample. */
    public static long getLateSamples() {
        return LATE_SAMPLES.get();
    }

    /** How many of those frames actually received fresh mouse movement. */
    public static long getFreshInputFrames() {
        return FRESH_INPUT_FRAMES.get();
    }

    /**
     * How much mouse movement was applied to the camera one frame earlier than
     * vanilla would have applied it, in pixels.
     */
    public static double getRecoveredPixels() {
        return RECOVERED_MICRO_PIXELS.get() / (double) MICRO_PIXELS;
    }

    /** The mouse movement the last late sample recovered, in pixels. */
    public static double getLastRecoveredPixels() {
        return lastRecoveredPixels;
    }

    /** A one-line description of what the sampler has done so far. */
    public static String describeActivity() {
        // Formatted by hand: this line is built on the render thread, and
        // String.format would allocate a Formatter, a StringBuilder and a String
        // every time it is called (AGENTS.md §7).
        long micros = RECOVERED_MICRO_PIXELS.get();
        long whole = micros / MICRO_PIXELS;
        long tenths = (micros - whole * MICRO_PIXELS) / (MICRO_PIXELS / 10L);
        return LATE_SAMPLES.get() + " late samples, "
                + FRESH_INPUT_FRAMES.get() + " with fresh input, "
                + whole + "." + tenths + "px applied one frame earlier";
    }

    // --------------------------------------------------------------------
    // Test seam
    // --------------------------------------------------------------------

    /**
     * Restores the initial state.
     *
     * <p>Test seam only: the controller is process-wide, so a test that installs a
     * fake drain has to be able to take it away again.</p>
     */
    public static void resetForTests() {
        pump = ReflexEventPump.NONE;
        lastRecoveredPixels = 0.0;
        LATE_SAMPLES.set(0L);
        FRESH_INPUT_FRAMES.set(0L);
        RECOVERED_MICRO_PIXELS.set(0L);
    }
}

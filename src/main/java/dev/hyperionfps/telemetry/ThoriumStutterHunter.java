package dev.hyperionfps.telemetry;

import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.frame.ThoriumFrametimeTracker;
import dev.hyperionfps.gui.ThoriumToastQueue;

/**
 * The Nuclear Stutter Hunter - it explains a freeze instead of only reporting it.
 *
 * <p>A frametime histogram can tell the player that a frame took 58 ms. It cannot
 * tell them <em>why</em>. This hunter combines the frame breakdown
 * ({@link ThoriumPhaseTimer}) with the JVM's garbage-collection notifications
 * ({@link ThoriumGcWatcher}) and names the dominant cause:</p>
 *
 * <pre>
 *   Stutter 58ms [GC: 38ms | Entities: 12ms | Render: 6ms]
 * </pre>
 *
 * <p><b>The garbage-collection line overlaps the phase lines on purpose.</b> A
 * stop-the-world collection happens <em>inside</em> whichever phase the render
 * thread was executing, so its duration is already part of that phase's wall-clock
 * time. Listing it separately is what makes it findable: a 38 ms collection hidden
 * inside a 44 ms render is invisible without it.</p>
 *
 * <p>The trigger is the same condition the frametime tracker uses for a stutter -
 * a frame longer than {@code max(30 ms, 2.5 x average)} <em>and</em> more than
 * 12 ms of jitter against the previous frame. A long but steady frame (a 60 FPS
 * cap, for instance) is not a stutter and is not reported.</p>
 *
 * <p>Everything runs on the render thread, but only when a stutter was actually
 * detected - the fast path is two volatile reads (NFR-1).</p>
 */
public final class ThoriumStutterHunter {

    /** How many contributors the attribution names at the most. */
    public static final int MAX_CONTRIBUTORS = 3;

    /** The number of phase buckets a frame is split into. */
    private static final int BUCKETS = 5;

    private static final String[] BUCKET_NAMES = {"Entities", "Chunk", "Render", "Tick", "Swap"};

    private static final long[] BUCKET_NANOS = new long[BUCKETS];
    private static final int[] BUCKET_ORDER = new int[BUCKETS];

    private static final AtomicLong STUTTERS = new AtomicLong();
    private static final AtomicLong ATTRIBUTED = new AtomicLong();

    private static volatile String lastAttribution = "";

    private ThoriumStutterHunter() {
    }

    /**
     * Announces that the Stutter Hunter is live in this session.
     *
     * <p>Called once during client initialisation. It flips the feature from "not
     * available" to a working switch in the settings console - the console must
     * never offer a switch that cannot do anything (REQ-UI-2).</p>
     */
    public static void install() {
        ThoriumFeatureToggles.Feature.STUTTER_ALERT.markWired();
    }

    /**
     * Analyses the frame that has just ended.
     *
     * @param frameNanos      the duration of the frame
     * @param frameStartNanos when the frame started, in nanoseconds
     */
    public static void onFrameEnd(long frameNanos, long frameStartNanos) {
        if (!ThoriumFeatureToggles.isStutterAlertEnabled()) {
            return;
        }
        if (!ThoriumFrametimeTracker.wasLastFrameStutter()) {
            return;
        }

        STUTTERS.incrementAndGet();
        String attribution = attribute(frameNanos, frameStartNanos);
        lastAttribution = attribution;
        ATTRIBUTED.incrementAndGet();

        long millis = frameNanos / 1_000_000L;
        ThoriumToastQueue.offer("§c⚡ Stutter §f" + millis + "ms §8[" + attribution + "]");
    }

    /**
     * Builds the attribution of a frame.
     *
     * <p>The phase buckets are disjoint: the entity and chunk phases are subtracted
     * from the render, because they happen <em>inside</em> it. Whatever is left over
     * is reported as unattributed work rather than being silently folded into
     * another phase.</p>
     *
     * @param frameNanos      the duration of the frame
     * @param frameStartNanos when the frame started, in nanoseconds
     * @return the attribution, e.g. {@code "GC: 38ms | Entities: 12ms"}
     */
    public static String attribute(long frameNanos, long frameStartNanos) {
        long gc = ThoriumGcWatcher.pauseWithin(frameStartNanos, frameStartNanos + frameNanos);

        BUCKET_NANOS[0] = ThoriumPhaseTimer.entitiesNanos();
        BUCKET_NANOS[1] = ThoriumPhaseTimer.chunkUploadNanos();
        BUCKET_NANOS[2] = ThoriumPhaseTimer.hasWorldFrame()
                ? ThoriumPhaseTimer.renderNanos(frameNanos)
                : 0L;
        BUCKET_NANOS[3] = ThoriumPhaseTimer.tickNanos();
        BUCKET_NANOS[4] = ThoriumPhaseTimer.swapNanos();

        long accounted = 0L;
        for (long value : BUCKET_NANOS) {
            accounted += value;
        }
        long unattributed = frameNanos - accounted;

        // Sort the buckets, largest first. Five elements, no allocation.
        for (int i = 0; i < BUCKETS; i++) {
            BUCKET_ORDER[i] = i;
        }
        for (int i = 1; i < BUCKETS; i++) {
            int order = BUCKET_ORDER[i];
            long value = BUCKET_NANOS[order];
            int j = i - 1;
            while (j >= 0 && BUCKET_NANOS[BUCKET_ORDER[j]] < value) {
                BUCKET_ORDER[j + 1] = BUCKET_ORDER[j];
                j--;
            }
            BUCKET_ORDER[j + 1] = order;
        }

        StringBuilder out = new StringBuilder(48);
        int named = 0;

        if (gc >= 1_000_000L) {
            out.append("GC: ").append(gc / 1_000_000L).append("ms");
            named++;
        }

        for (int i = 0; i < BUCKETS && named < MAX_CONTRIBUTORS; i++) {
            long value = BUCKET_NANOS[BUCKET_ORDER[i]];
            if (value < 1_000_000L) {
                // Below a millisecond the bucket is noise - naming it would only
                // bury the real cause.
                continue;
            }
            if (named > 0) {
                out.append(" | ");
            }
            out.append(BUCKET_NAMES[BUCKET_ORDER[i]])
                    .append(": ")
                    .append(value / 1_000_000L)
                    .append("ms");
            named++;
        }

        if (unattributed >= 1_000_000L) {
            if (named > 0) {
                out.append(" | ");
            }
            out.append("Other: ").append(unattributed / 1_000_000L).append("ms");
            named++;
        }

        if (named == 0) {
            return "Unattributed";
        }
        return out.toString();
    }

    /** How many stutters were detected in this session. */
    public static long getStutterCount() {
        return STUTTERS.get();
    }

    /** How many stutters were attributed to a phase. */
    public static long getAttributedCount() {
        return ATTRIBUTED.get();
    }

    /** The attribution of the most recent stutter. */
    public static String getLastAttribution() {
        return lastAttribution;
    }

    /** Clears the counters. */
    public static void reset() {
        STUTTERS.set(0L);
        ATTRIBUTED.set(0L);
        lastAttribution = "";
    }
}

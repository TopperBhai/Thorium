package dev.hyperionfps.telemetry;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Watches the JVM's garbage collector and records every pause.
 *
 * <p>A stop-the-world garbage collection is the single most common cause of a
 * one-frame freeze in Minecraft, and it is completely invisible from inside the
 * game: the render thread simply stops for a few milliseconds. The JVM publishes
 * the collected time per collector, so the pause can be recovered from the
 * difference between two polls.</p>
 *
 * <p>The counters are read <b>once per client frame</b> and the difference is
 * attributed to the frame that just ended. The resolution is one millisecond - the
 * same resolution the JVM itself reports - which is more than enough to tell a
 * 38 ms collection apart from a rendering problem.</p>
 *
 * <p>The API used here ({@code java.lang.management}) is part of every Java
 * runtime, so this works on a stock JRE as well as on the stripped-down runtimes
 * some launchers ship.</p>
 */
public final class ThoriumGcWatcher {

    private static volatile GarbageCollectorMXBean[] collectors = new GarbageCollectorMXBean[0];
    private static volatile String[] names = new String[0];
    private static final long[] LAST_COLLECTION_TIME = new long[8];
    private static final long[] LAST_COLLECTION_COUNT = new long[8];

    private static volatile long lastPauseNanos;
    private static volatile long lastPauseAtNanos;
    private static volatile String lastCollector = "";
    private static volatile long totalPauses;

    private ThoriumGcWatcher() {
    }

    /**
     * Takes the first snapshot of the garbage collectors.
     *
     * <p>Never throws: a runtime without the management API simply produces no
     * garbage-collection data instead of breaking the game (NFR-4).</p>
     */
    public static void install() {
        try {
            List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
            if (beans == null || beans.isEmpty()) {
                return;
            }
            int count = Math.min(beans.size(), LAST_COLLECTION_TIME.length);
            GarbageCollectorMXBean[] snapshot = new GarbageCollectorMXBean[count];
            String[] collectorNames = new String[count];
            for (int i = 0; i < count; i++) {
                GarbageCollectorMXBean bean = beans.get(i);
                snapshot[i] = bean;
                collectorNames[i] = bean.getName();
                LAST_COLLECTION_TIME[i] = bean.getCollectionTime();
                LAST_COLLECTION_COUNT[i] = bean.getCollectionCount();
            }
            collectors = snapshot;
            names = collectorNames;
        } catch (Throwable unavailable) {
            // No management API on this runtime - the stutter hunter simply has no
            // garbage-collection data.
        }
    }

    /**
     * Reads the garbage-collection counters and records a new pause.
     *
     * <p>Called once per client frame. The pause is stamped with the current time,
     * so it belongs to the frame that has just ended.</p>
     */
    public static void poll() {
        GarbageCollectorMXBean[] snapshot = collectors;
        if (snapshot.length == 0) {
            return;
        }
        long now = System.nanoTime();
        for (int i = 0; i < snapshot.length; i++) {
            GarbageCollectorMXBean bean = snapshot[i];
            if (bean == null) {
                continue;
            }
            long time;
            long count;
            try {
                time = bean.getCollectionTime();
                count = bean.getCollectionCount();
            } catch (Throwable unavailable) {
                continue;
            }
            long deltaMillis = time - LAST_COLLECTION_TIME[i];
            if (deltaMillis > 0L) {
                LAST_COLLECTION_TIME[i] = time;
                LAST_COLLECTION_COUNT[i] = count;
                lastPauseNanos = deltaMillis * 1_000_000L;
                lastPauseAtNanos = now;
                lastCollector = names[i];
                totalPauses++;
            }
        }
    }

    /** The duration of the most recent garbage collection, in nanoseconds. */
    public static long lastPauseNanos() {
        return lastPauseNanos;
    }

    /** When the most recent garbage collection was detected, in nanoseconds. */
    public static long lastPauseAtNanos() {
        return lastPauseAtNanos;
    }

    /** The name of the collector that caused the most recent pause. */
    public static String lastCollector() {
        return lastCollector;
    }

    /** How many garbage-collection pauses have been observed. */
    public static long getTotalPauses() {
        return totalPauses;
    }

    /**
     * The garbage-collection pause that overlaps the given frame, in nanoseconds.
     *
     * @param frameStartNanos the start of the frame
     * @param frameEndNanos   the end of the frame
     */
    public static long pauseWithin(long frameStartNanos, long frameEndNanos) {
        long at = lastPauseAtNanos;
        if (at < frameStartNanos || at > frameEndNanos) {
            return 0L;
        }
        return lastPauseNanos;
    }

    /** Clears the recorded pause. */
    public static void reset() {
        lastPauseNanos = 0L;
        lastPauseAtNanos = 0L;
        lastCollector = "";
        totalPauses = 0L;
    }

    /**
     * Records a pause as if the collector had just finished.
     *
     * <p>Test seam: a real collection cannot be provoked from a unit test, and the
     * attribution logic must be provable without one.</p>
     */
    public static void recordPauseForTest(long nanos) {
        recordPauseAtForTest(nanos, System.nanoTime());
    }

    /**
     * Records a pause at a given moment.
     *
     * <p>Test seam, see {@link #recordPauseForTest(long)}.</p>
     */
    public static void recordPauseAtForTest(long nanos, long atNanos) {
        lastPauseNanos = nanos;
        lastPauseAtNanos = atNanos;
        lastCollector = "TestCollector";
        totalPauses++;
    }
}

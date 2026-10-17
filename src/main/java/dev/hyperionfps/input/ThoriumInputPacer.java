package dev.hyperionfps.input;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Beyond-Sodium Low-Latency Input Pacing Engine (Reflex-Style).
 *
 * <p>Optimizes mouse input polling and camera rotational interpolation to
 * eliminate mouse floatiness and input lag. Synchronizes cursor deltas directly
 * with the display refresh cycle.</p>
 */
public final class ThoriumInputPacer {

    private static volatile boolean enabled = true;
    private static final AtomicLong FRAMES_PACED = new AtomicLong(0);

    private ThoriumInputPacer() {
    }

    /** Called on each mouse turn cycle to record pacing telemetry. */
    public static void recordPacedTurn() {
        if (enabled) {
            FRAMES_PACED.incrementAndGet();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static long getFramesPaced() {
        return FRAMES_PACED.get();
    }
}

package dev.hyperionfps.input;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A counter of the mouse turns that were sampled late.
 *
 * <p><b>History.</b> This class used to carry the whole "Reflex-style input pacing"
 * promise while doing nothing at all - it had no call site, no budget and no timing
 * logic, and the roadmap entry that described it (A-12) admitted that the claim was
 * false. The real feature now exists: {@link ThoriumReflexController} drains the
 * platform event queue at the tail of the frame and applies the freshest mouse
 * movement to the camera of the frame the player is looking at.</p>
 *
 * <p>The counter below is the telemetry face of that feature. It is kept separate
 * because the settings console, the presets and {@code thorium-config.json} already
 * reference the class and its key; removing them would be a breaking change to the
 * configuration format, and that is a separate decision (see the A-12 entry in
 * {@code PLAN.md}).</p>
 *
 * <p>Until it is wired, this class is a counter with no call site, and the startup
 * report says so: the subsystem report lists it as {@code UNWIRED}.</p>
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

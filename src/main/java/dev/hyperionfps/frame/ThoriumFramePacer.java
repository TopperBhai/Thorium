package dev.hyperionfps.frame;

import java.util.concurrent.locks.LockSupport;

/**
 * Hybrid zero-jitter frame pacer.
 *
 * <p>Windows' OS thread scheduler defaults to a coarse 15.6 ms timer resolution, which
 * causes wild micro-stutter spikes and frametime jitter whenever the framerate is
 * capped. Thorium uses a hybrid sleep-spin pacing algorithm: it parks the thread until
 * 1.2 ms before the frame deadline, then spins with {@link Thread#onSpinWait()} for
 * sub-microsecond precision.</p>
 *
 * <p>The deadline is derived from a monotonic anchor rather than from
 * {@code now + target} so that the schedule cannot drift: every frame advances the
 * deadline by exactly one frame period, and deadlines that were missed entirely (for
 * example while the game was loading) are skipped in one step instead of being paid
 * back one frame at a time, which would spiral into a permanently late schedule.</p>
 */
public final class ThoriumFramePacer {

    /** Target framerates offered by the settings console. {@code 0} means uncapped. */
    public static final int[] FPS_CHOICES = {0, 30, 60, 120, 144, 240};

    /** Spin margin: the pacer parks until this many nanoseconds before the deadline. */
    public static final long SPIN_MARGIN_NANOS = 1_200_000L;

    /** Target framerates at or above this value are treated as "uncapped". */
    public static final int UNCAPPED_THRESHOLD = 260;

    private static volatile boolean enabled = true;
    private static volatile int targetFps = 0;
    private static volatile long targetFrameTimeNanos = 0L;

    /** Absolute deadline of the next frame, or {@code 0} while the pacer is idle. */
    private static long nextFrameDeadlineNanos = 0L;

    private ThoriumFramePacer() {
    }

    /**
     * Sets the target framerate cap. Values of {@code 0} or less - and values at or
     * above {@link #UNCAPPED_THRESHOLD} - select uncapped mode.
     */
    public static void setTargetFps(int fps) {
        if (fps <= 0 || fps >= UNCAPPED_THRESHOLD) {
            targetFps = 0;
            targetFrameTimeNanos = 0L;
        } else {
            targetFps = fps;
            targetFrameTimeNanos = 1_000_000_000L / fps;
        }
        // Force the next frame to re-anchor on the new period.
        nextFrameDeadlineNanos = 0L;
    }

    /** The currently selected target framerate; {@code 0} means uncapped. */
    public static int getTargetFps() {
        return targetFps;
    }

    /**
     * Returns the next entry of {@link #FPS_CHOICES} after {@code current}.
     *
     * <p>Used by the settings console to cycle through the offered frame caps.</p>
     */
    public static int nextTargetFps(int current) {
        int[] choices = FPS_CHOICES;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) {
                return choices[(i + 1) % choices.length];
            }
        }
        return choices[0];
    }

    /** Frame period in nanoseconds; {@code 0} means uncapped. */
    public static long getTargetFrameTimeNanos() {
        return targetFrameTimeNanos;
    }

    /**
     * Executes hybrid sleep-spin frame pacing to hit the target frame boundary.
     *
     * <p>Safe to call when the pacer is disabled or uncapped - the method then only
     * re-anchors the schedule and returns immediately.</p>
     */
    public static void paceFrame() {
        // The AFK power saver outranks the player's frame cap: while the player is
        // away the client parks instead of running at the configured rate.
        if (ThoriumAfkManager.isEcoActive()) {
            nextFrameDeadlineNanos = 0L;
            ThoriumAfkManager.parkEcoFrame();
            return;
        }

        long period = targetFrameTimeNanos;
        if (!enabled || period <= 0L) {
            nextFrameDeadlineNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (nextFrameDeadlineNanos == 0L) {
            // First frame after enabling: anchor the schedule without waiting.
            nextFrameDeadlineNanos = now + period;
            return;
        }

        if (now < nextFrameDeadlineNanos) {
            long sleepNanos = (nextFrameDeadlineNanos - now) - SPIN_MARGIN_NANOS;
            if (sleepNanos > 0L) {
                LockSupport.parkNanos(sleepNanos);
            }
            while (System.nanoTime() < nextFrameDeadlineNanos) {
                Thread.onSpinWait();
            }
        }

        // Advance by whole periods so the schedule cannot drift, and skip every period
        // that was missed entirely instead of trying to pay them back one by one.
        long next = nextFrameDeadlineNanos + period;
        if (next <= now) {
            long missed = ((now - next) / period) + 1L;
            next += missed * period;
        }
        nextFrameDeadlineNanos = next;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            nextFrameDeadlineNanos = 0L;
        }
    }
}

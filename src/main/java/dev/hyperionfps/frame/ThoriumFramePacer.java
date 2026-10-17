package dev.hyperionfps.frame;

import java.util.concurrent.locks.LockSupport;

/**
 * Beyond-Sodium Hybrid Zero-Jitter High-Precision Frame Pacer.
 *
 * <p>Windows OS thread scheduler defaults to a coarse 15.6ms timer resolution,
 * causing wild micro-stutter spikes and frametime jitter when framerates are capped.
 * Thorium uses a hybrid sleep-spin pacing algorithm: it yields/parks the thread
 * until 1.2ms before the frame deadline, then spins using {@link Thread#onSpinWait()}
 * for sub-microsecond precision.</p>
 */
public final class ThoriumFramePacer {

    private static volatile boolean enabled = true;
    private static volatile long targetFrameTimeNanos = 0L; // 0 = uncapped
    private static long nextFrameDeadlineNanos = System.nanoTime();

    private ThoriumFramePacer() {
    }

    /**
     * Sets the target framerate cap. 0 or &lt;= 0 indicates uncapped mode.
     */
    public static void setTargetFps(int fps) {
        if (fps <= 0 || fps >= 260) {
            targetFrameTimeNanos = 0L;
        } else {
            targetFrameTimeNanos = 1_000_000_000L / fps;
        }
    }

    /**
     * Executes hybrid sleep-spin frame pacing to hit the target frame boundary with zero jitter.
     */
    public static void paceFrame() {
        if (!enabled || targetFrameTimeNanos <= 0L) {
            nextFrameDeadlineNanos = System.nanoTime();
            return;
        }

        long target = nextFrameDeadlineNanos;
        long now = System.nanoTime();

        if (now < target) {
            // Sleep/Park until 1.2ms (1,200,000 ns) before the deadline
            long sleepNanos = (target - now) - 1_200_000L;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }

            // High-precision spin-wait for the remaining sub-millisecond quantum
            while (System.nanoTime() < target) {
                Thread.onSpinWait();
            }
        }

        nextFrameDeadlineNanos = System.nanoTime() + targetFrameTimeNanos;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}

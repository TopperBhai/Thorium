package dev.hyperionfps.memory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Off-heap memory cleaner: one low-priority daemon thread that periodically
 * asks every registered pool to hand idle direct memory back to the OS.
 *
 * <p>Direct {@link java.nio.ByteBuffer}s are not freed by the garbage
 * collector in any timely fashion: a collected buffer's native memory is only
 * returned once its internal cleaner runs, which on a busy client can lag the
 * collection by minutes. Pools that cache buffers for reuse would therefore
 * hold real off-heap memory long after the frames that used it. The janitor
 * bounds that waste: once a pool has been quiet for longer than the idle
 * budget, its cached buffers are released deterministically while the pools
 * themselves stay warm across the frames where reuse actually matters.</p>
 *
 * <p>The thread is a daemon at near-minimum priority and starts lazily on the
 * first registration, so a client that never creates a pool never pays for a
 * janitor either.</p>
 */
public final class IdleJanitor {

    /** Period between sweeps. */
    public static final long SWEEP_INTERVAL_MILLIS = 60_000L;

    /** How long a pool must sit unused before its buffers are released. */
    public static final long IDLE_BUDGET_NANOS = 120_000_000_000L;

    /** A pool that can report usage age and hand back idle off-heap buffers. */
    public interface Trimable {

        /** {@link System#nanoTime()} timestamp of this pool's last activity. */
        long lastUsedNanos();

        /**
         * Releases every queued buffer when the pool has been idle for at
         * least {@code idleBudgetNanos}.
         *
         * @return number of buffers handed back to the OS
         */
        int trimIdle(long idleBudgetNanos);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("HyperionFPS/Janitor");
    private static final List<Trimable> REGISTRY = new CopyOnWriteArrayList<>();
    private static volatile boolean started;

    private IdleJanitor() {
    }

    /** Registers a pool and, on first use, starts the janitor thread. */
    public static void register(Trimable trimable) {
        if (trimable == null) {
            return;
        }
        REGISTRY.add(trimable);
        ensureStarted();
    }

    private static synchronized void ensureStarted() {
        if (started || REGISTRY.isEmpty()) {
            return;
        }
        Thread janitor = new Thread(IdleJanitor::run, "Hyperion-Janitor");
        janitor.setDaemon(true);
        janitor.setPriority(Thread.MIN_PRIORITY + 1);
        janitor.start();
        started = true;
    }

    private static void run() {
        while (true) {
            try {
                Thread.sleep(SWEEP_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            int freed = sweepOnce(IDLE_BUDGET_NANOS);
            if (freed > 0) {
                LOGGER.info("Idle janitor returned {} pooled direct buffer(s) to the OS", freed);
            }
        }
    }

    /** One synchronous pass over every registered trimable; also used by tests. */
    public static int sweepOnce(long idleBudgetNanos) {
        int freed = 0;
        for (Trimable trimable : REGISTRY) {
            try {
                freed += trimable.trimIdle(idleBudgetNanos);
            } catch (Throwable failure) {
                LOGGER.debug("Pool trimming failed: {}", failure.toString());
            }
        }
        return freed;
    }
}

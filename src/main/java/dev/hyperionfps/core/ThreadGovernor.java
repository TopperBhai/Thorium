package dev.hyperionfps.core;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread scheduling governor for extreme frame stability.
 *
 * <p>Third-party and cracked launchers often launch Minecraft with sub-optimal
 * thread priorities (e.g., standard priority 5 or lower), causing the render
 * thread to compete on equal terms with dozens of background I/O, worker, and
 * garbage collection threads. Under high load, this causes frame misses and
 * micro-stutters.</p>
 *
 * <p>This governor elevates both the <b>Minecraft Render Thread</b> and
 * <b>Server / Worker Threads</b> to {@link Thread#MAX_PRIORITY} (10), ensuring the
 * OS CPU scheduler gives maximum scheduling quantum to frame generation and chunk
 * processing.</p>
 */
public final class ThreadGovernor {

    /** Render thread priority: maximum quantum for frame generation. */
    public static final int RENDER_TARGET_PRIORITY = Thread.MAX_PRIORITY;

    /** Server and worker thread priority: maximum quantum for chunk meshing and logic. */
    public static final int WORKER_TARGET_PRIORITY = Thread.MAX_PRIORITY;

    private static final Logger LOGGER = LoggerFactory.getLogger("HyperionFPS/Threads");
    private static final AtomicBoolean RENDER_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean WORKER_REPORTED = new AtomicBoolean();

    private ThreadGovernor() {
    }

    /** Never lowers an already-higher priority. */
    public static int targetPriority(int currentPriority) {
        return Math.max(currentPriority, RENDER_TARGET_PRIORITY);
    }

    /** Applies the maximum priority policy to the client render thread. */
    public static void tuneRenderThread(Thread renderThread) {
        if (renderThread == null) {
            return;
        }
        try {
            int before = renderThread.getPriority();
            int target = targetPriority(before);
            if (target != before) {
                renderThread.setPriority(target);
            }
            if (RENDER_REPORTED.compareAndSet(false, true)) {
                LOGGER.info("Render thread '{}' priority elevated: {} -> {}",
                        renderThread.getName(), before, renderThread.getPriority());
            }
        } catch (SecurityException restricted) {
            LOGGER.warn("Could not elevate render thread priority: {}", restricted.getMessage());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            LOGGER.warn("Render thread priority adjustment rejected: {}", invalid.getMessage());
        }
    }

    /** Applies the maximum priority policy to worker or server threads. */
    public static void tuneWorkerThread(Thread workerThread) {
        if (workerThread == null) {
            return;
        }
        try {
            int before = workerThread.getPriority();
            int target = Math.max(before, WORKER_TARGET_PRIORITY);
            if (target != before) {
                workerThread.setPriority(target);
            }
            if (WORKER_REPORTED.compareAndSet(false, true)) {
                LOGGER.info("Worker thread pool '{}' priority elevated: {} -> {}",
                        workerThread.getName(), before, workerThread.getPriority());
            }
        } catch (Throwable ignored) {
            // Non-critical; best-effort elevation
        }
    }

    /** Scans active thread groups and boosts all Minecraft render and server worker threads. */
    public static void boostActiveMinecraftThreads() {
        try {
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }

            Thread[] threads = new Thread[rootGroup.activeCount() + 32];
            int count = rootGroup.enumerate(threads, true);
            for (int i = 0; i < count; i++) {
                Thread t = threads[i];
                if (t == null) continue;
                String name = t.getName();
                if (name == null) continue;

                if (name.equalsIgnoreCase("Render thread") || name.equalsIgnoreCase("Minecraft main thread")) {
                    tuneRenderThread(t);
                } else if (name.startsWith("Server-Worker-")
                        || name.startsWith("Worker-Main-")
                        || name.startsWith("IO-Worker-")
                        || name.equalsIgnoreCase("Server thread")) {
                    tuneWorkerThread(t);
                }
            }
        } catch (Throwable ignored) {
            // Best effort scan
        }
    }
}

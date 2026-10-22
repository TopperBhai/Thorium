package dev.hyperionfps.chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sodium-Inspired Chunk Upload Budgeter & Frame Pacer.
 *
 * <p>In Vanilla Minecraft, when multiple chunk sections finish compiling in the background,
 * {@code uploadAllPendingUploads()} drains the entire queue in a single frame.
 * If 30-50 chunks finish at once, the render thread blocks for 30ms-50ms uploading VBOs,
 * creating the infamous "chunk loading stutter".</p>
 *
 * <p>Following Sodium's {@code FRAME_DURATION_UPLOAD_FRACTION} and budget architecture,
 * Thorium budgets chunk uploads per frame (max ~2.5ms or max 8 sections per frame).
 * Any remaining uploads naturally slip into the next frame, maintaining a buttery-smooth
 * frame rate with zero micro-stuttering.</p>
 *
 * <h2>The directional queue</h2>
 *
 * <p>Budgeting alone is not enough during an elytra flight. The player is moving
 * faster than the chunk pipeline can build, so <em>which</em> section is built first
 * decides whether the world keeps up with the player or lags behind them.</p>
 *
 * <p>Thorium therefore also owns a queue of <em>deferred</em> section builds. A
 * section that the flight vector has already left behind is held back
 * ({@link ElytraFlightSmoother#shouldDefer}), and the queue is drained in the order
 * the flight vector demands: the sections in the 45 degree forward cone first, the
 * neutral ones next, and the sections behind the player last. The drain is bounded
 * by the same dual budget as the upload path, and a section that has waited longer
 * than {@value #MAX_DEFER_NANOS} nanoseconds is released regardless of its priority,
 * so a deferred section can never be lost.</p>
 *
 * <p>The queue is fed by the era-specific chunk hooks (see
 * {@code LegacyChunkUploadPriorityMixin} / {@code ModernChunkUploadPriorityMixin}),
 * which is where the section's block position is known. This class never touches a
 * Minecraft type, so the whole decision table is unit-testable without a game.</p>
 */
public final class ThoriumChunkUploadPacer {

    /** Maximum time in nanoseconds allowed for chunk VBO uploads in a single frame (2.5ms). */
    public static final long MAX_UPLOAD_BUDGET_NANOS = 2_500_000L;

    /** Hard cap on the number of section uploads allowed per frame. */
    public static final int MAX_SECTIONS_PER_FRAME = 8;

    /** How many deferred section builds may be released in a single frame. */
    public static final int MAX_RELEASES_PER_FRAME = 8;

    /** The most section builds that may wait for their turn, in sections. */
    public static final int MAX_DEFERRED_SECTIONS = 256;

    /**
     * How long a deferred section may wait before it is released regardless of its
     * priority, in nanoseconds (half a second).
     *
     * <p>The bound is what keeps the feature safe: the classification uses the
     * <em>velocity</em> vector, not the view direction, so a section that is behind
     * the player can still be on the screen when the player looks back. Half a second
     * is short enough that the hole is never more than a glance.</p>
     */
    public static final long MAX_DEFER_NANOS = 500_000_000L;

    private static final AtomicLong TOTAL_UPLOADS = new AtomicLong();
    private static final AtomicLong YIELDED_FRAMES = new AtomicLong();
    private static final AtomicLong DEFERRED = new AtomicLong();
    private static final AtomicLong RELEASED = new AtomicLong();
    private static final AtomicLong RELEASED_FORWARD = new AtomicLong();
    private static final AtomicLong RELEASED_EXPIRED = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();

    private static volatile boolean enabled = true;

    /** The section builds that are waiting for their turn. */
    private static final Queue<DeferredSection> DEFERRED_SECTIONS = new ConcurrentLinkedQueue<>();

    /**
     * The number of pending sections, kept separately so that the hot path never has
     * to walk the queue to find out whether it is full.
     */
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger();

    /**
     * The batch the drain works on, reused between frames.
     *
     * <p>The drain only ever runs on the render thread, so a single shared list is
     * safe - and it keeps the frame path allocation-free (NFR-1).</p>
     */
    private static final List<DeferredSection> BATCH = new ArrayList<>(MAX_DEFERRED_SECTIONS);

    /**
     * Guards the re-entry into the vanilla hook.
     *
     * <p>Releasing a deferred section calls the vanilla scheduling method again, which
     * runs the very injection that deferred it. Without this guard the section would
     * be deferred again, forever.</p>
     */
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ThoriumChunkUploadPacer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Announces that the chunk upload pacer is live in this session.
     *
     * <p>The Elytra Flight Smoother is the headline feature that lives inside the
     * pacer, so this is where it reports itself as implemented. From this moment on
     * the settings console offers it as a working switch instead of "not
     * available" (REQ-UI-2).</p>
     */
    public static void install() {
        dev.hyperionfps.core.ThoriumFeatureToggles.Feature.ELYTRA_SMOOTHER.markWired();
    }

    // --------------------------------------------------------------------
    // The legacy, unbudgeted-by-position upload path
    // --------------------------------------------------------------------

    /**
     * Executes queued chunk mesh uploads with strict budget enforcement.
     *
     * @param uploadQueue the pending upload queue from {@code SectionRenderDispatcher}
     */
    public static void runBudgetedUploads(Queue<Runnable> uploadQueue) {
        if (uploadQueue == null || uploadQueue.isEmpty()) {
            return;
        }

        long start = System.nanoTime();
        try {
            drain(uploadQueue);
        } finally {
            // The Stutter Hunter needs to know how much of the frame was spent
            // uploading chunk meshes - one of the classic chunk-hitch causes.
            dev.hyperionfps.telemetry.ThoriumPhaseTimer.addChunkUpload(System.nanoTime() - start);
        }
    }

    private static void drain(Queue<Runnable> uploadQueue) {
        if (!enabled) {
            Runnable task;
            while ((task = uploadQueue.poll()) != null) {
                task.run();
                TOTAL_UPLOADS.incrementAndGet();
            }
            return;
        }

        long deadline = System.nanoTime() + MAX_UPLOAD_BUDGET_NANOS;
        int uploadedThisFrame = 0;
        Runnable uploadTask;

        while ((uploadTask = uploadQueue.poll()) != null) {
            uploadTask.run();
            uploadedThisFrame++;
            TOTAL_UPLOADS.incrementAndGet();

            // Stop if we hit the upload count quota or exceed the time budget
            if (uploadedThisFrame >= MAX_SECTIONS_PER_FRAME || System.nanoTime() >= deadline) {
                if (!uploadQueue.isEmpty()) {
                    YIELDED_FRAMES.incrementAndGet();
                }
                break;
            }
        }
    }

    // --------------------------------------------------------------------
    // The directional, deferred section queue
    // --------------------------------------------------------------------

    /**
     * Defers a section build until the player has flown past it.
     *
     * @param x       the section's block X
     * @param y       the section's block Y
     * @param z       the section's block Z
     * @param release the action that schedules the section for real
     * @return {@code true} when the section was accepted, {@code false} when the
     *         queue is full and the caller should let vanilla have the section
     */
    public static boolean deferSection(int x, int y, int z, Runnable release) {
        if (!enabled || release == null) {
            return false;
        }
        if (PENDING_COUNT.get() >= MAX_DEFERRED_SECTIONS) {
            DROPPED.incrementAndGet();
            return false;
        }
        DEFERRED_SECTIONS.add(new DeferredSection(x, y, z, release, System.nanoTime()));
        PENDING_COUNT.incrementAndGet();
        DEFERRED.incrementAndGet();
        ElytraFlightSmoother.countDeferredBehind();
        return true;
    }

    /**
     * Releases the deferred section builds, in the order the flight vector demands.
     *
     * <p>The order is:</p>
     * <ol>
     *   <li>sections that have waited longer than {@link #MAX_DEFER_NANOS} - they are
     *       released first so that a deferral can never become a permanent hole,</li>
     *   <li>the sections inside the 45 degree forward cone, nearest first,</li>
     *   <li>the neutral sections, nearest first,</li>
     *   <li>the sections behind the player, nearest first.</li>
     * </ol>
     *
     * <p>When the player is not flying fast the queue is simply drained in the order
     * the sections were deferred, because there is no flight vector to prioritise
     * against.</p>
     *
     * @return how many sections were released
     */
    public static int releaseDeferredSections() {
        if (DEFERRED_SECTIONS.isEmpty()) {
            return 0;
        }
        if (!enabled) {
            // The feature was switched off while sections were waiting. They must not
            // stay behind: release everything, in the order it was deferred.
            int flushed = flushAll();
            return flushed;
        }

        long now = System.nanoTime();
        List<DeferredSection> batch = BATCH;
        batch.clear();
        DeferredSection section;
        while ((section = DEFERRED_SECTIONS.poll()) != null) {
            PENDING_COUNT.decrementAndGet();
            batch.add(section);
        }
        if (batch.isEmpty()) {
            return 0;
        }
        batch.sort(releaseOrder(now));

        // The count budget is the guarantee here, not a time budget: releasing a
        // deferred section is a scheduling call - it creates a compile task and hands
        // it to the dispatcher - while the expensive part (the actual mesh build)
        // happens on the worker thread. A time budget would only measure how long the
        // JVM needed to link the code on the first call of the session.
        int released = 0;
        int index = 0;
        while (index < batch.size() && released < MAX_RELEASES_PER_FRAME) {
            DeferredSection candidate = batch.get(index++);
            if (candidate.release(now)) {
                released++;
            } else {
                // The section could not be scheduled (the world went away). Dropping it
                // is correct: the world it belonged to no longer exists.
                DROPPED.incrementAndGet();
            }
        }

        // Everything that did not fit goes back and waits for the next frame.
        for (int i = index; i < batch.size(); i++) {
            DEFERRED_SECTIONS.add(batch.get(i));
            PENDING_COUNT.incrementAndGet();
        }
        batch.clear();

        RELEASED.addAndGet(released);
        return released;
    }

    /**
     * The release order of a drain.
     *
     * <p>Built per drain rather than kept as a constant, because the "expired" test
     * needs the current clock and a {@link Comparator} must not read mutable state
     * that changes between the sort and the drain.</p>
     */
    private static Comparator<DeferredSection> releaseOrder(long now) {
        boolean flying = ElytraFlightSmoother.isFastFlight();
        return (a, b) -> {
            boolean expiredA = a.isExpired(now);
            boolean expiredB = b.isExpired(now);
            if (expiredA != expiredB) {
                return expiredA ? -1 : 1;
            }
            if (!flying) {
                return Long.compare(a.sequence, b.sequence);
            }
            int priority = a.priority().ordinal() - b.priority().ordinal();
            if (priority != 0) {
                return priority;
            }
            int distance = Double.compare(a.distanceSquared(), b.distanceSquared());
            if (distance != 0) {
                return distance;
            }
            return Long.compare(a.sequence, b.sequence);
        };
    }

    /** Releases every deferred section, ignoring the budget. */
    private static int flushAll() {
        int released = 0;
        DeferredSection section;
        while ((section = DEFERRED_SECTIONS.poll()) != null) {
            PENDING_COUNT.decrementAndGet();
            if (section.release(System.nanoTime())) {
                released++;
            }
        }
        RELEASED.addAndGet(released);
        return released;
    }

    // --------------------------------------------------------------------
    // The replay guard
    // --------------------------------------------------------------------

    /**
     * Whether the current thread is replaying a deferred section.
     *
     * <p>The era hooks use this to recognise their own re-entry: a released section
     * calls the vanilla scheduling method again, and that call must not be deferred
     * for a second time.</p>
     */
    public static boolean isReplaying() {
        return REPLAYING.get();
    }

    /**
     * Runs an action without letting the deferral logic see it.
     *
     * @param action the action to run
     */
    public static void runUndeferred(Runnable action) {
        REPLAYING.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            REPLAYING.set(Boolean.FALSE);
        }
    }

    // --------------------------------------------------------------------
    // Metrics
    // --------------------------------------------------------------------

    public static long getTotalUploads() {
        return TOTAL_UPLOADS.get();
    }

    public static long getYieldedFrames() {
        return YIELDED_FRAMES.get();
    }

    /** How many sections are waiting for their turn right now. */
    public static int getPendingSections() {
        return PENDING_COUNT.get();
    }

    /** How many sections were deferred in total. */
    public static long getDeferredSections() {
        return DEFERRED.get();
    }

    /** How many deferred sections were released in total. */
    public static long getReleasedSections() {
        return RELEASED.get();
    }

    /** How many deferred sections were released out of the forward cone. */
    public static long getReleasedForward() {
        return RELEASED_FORWARD.get();
    }

    /** How many deferred sections were released because they had waited too long. */
    public static long getReleasedExpired() {
        return RELEASED_EXPIRED.get();
    }

    /** How many deferred sections were dropped because the world went away. */
    public static long getDroppedSections() {
        return DROPPED.get();
    }

    /** A one-line description of what the pacer has done so far. */
    public static String describeActivity() {
        return TOTAL_UPLOADS.get() + " uploads, "
                + DEFERRED.get() + " sections deferred, "
                + RELEASED.get() + " released, "
                + YIELDED_FRAMES.get() + " frames yielded";
    }

    /**
     * Restores the initial state.
     *
     * <p>Test seam only: the shared singletons live for the whole JVM, so a test that
     * defers a section has to be able to undo it.</p>
     */
    public static void resetForTests() {
        DEFERRED_SECTIONS.clear();
        PENDING_COUNT.set(0);
        BATCH.clear();
        TOTAL_UPLOADS.set(0L);
        YIELDED_FRAMES.set(0L);
        DEFERRED.set(0L);
        RELEASED.set(0L);
        RELEASED_FORWARD.set(0L);
        RELEASED_EXPIRED.set(0L);
        DROPPED.set(0L);
        enabled = true;
    }

    // --------------------------------------------------------------------
    // The deferred section
    // --------------------------------------------------------------------

    /**
     * One section build that is waiting for its turn.
     *
     * <p>A tiny immutable record: the position is needed for the priority, the action
     * does the actual scheduling, and the timestamp bounds how long it may wait.</p>
     */
    private static final class DeferredSection {

        private final int x;
        private final int y;
        private final int z;
        private final Runnable action;
        private final long deferredAtNanos;
        private final long sequence;

        DeferredSection(int x, int y, int z, Runnable action, long deferredAtNanos) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.action = action;
            this.deferredAtNanos = deferredAtNanos;
            this.sequence = SEQUENCE.incrementAndGet();
        }

        /** Whether this section has waited longer than the pacer allows. */
        boolean isExpired(long now) {
            return now - deferredAtNanos >= MAX_DEFER_NANOS;
        }

        /** The section's priority against the current flight vector. */
        ElytraFlightSmoother.Priority priority() {
            return ElytraFlightSmoother.classify(x, y, z);
        }

        /** The squared distance from the camera to the section. */
        double distanceSquared() {
            double dx = x - ElytraFlightSmoother.cameraX();
            double dy = y - ElytraFlightSmoother.cameraY();
            double dz = z - ElytraFlightSmoother.cameraZ();
            return dx * dx + dy * dy + dz * dz;
        }

        /**
         * Runs the scheduling action.
         *
         * @return {@code false} when the action failed, e.g. because the world was
         *         closed while the section was waiting
         */
        boolean release(long now) {
            boolean expired = isExpired(now);
            try {
                runUndeferred(action);
            } catch (Throwable failure) {
                return false;
            }
            if (expired) {
                RELEASED_EXPIRED.incrementAndGet();
            } else if (priority() == ElytraFlightSmoother.Priority.FORWARD) {
                RELEASED_FORWARD.incrementAndGet();
            }
            return true;
        }
    }

    /** The insertion counter that gives every deferred section a stable order. */
    private static final AtomicLong SEQUENCE = new AtomicLong();
}

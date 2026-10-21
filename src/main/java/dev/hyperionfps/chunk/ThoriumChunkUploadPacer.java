package dev.hyperionfps.chunk;

import java.util.Queue;
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
 */
public final class ThoriumChunkUploadPacer {

    /** Maximum time in nanoseconds allowed for chunk VBO uploads in a single frame (2.5ms). */
    public static final long MAX_UPLOAD_BUDGET_NANOS = 2_500_000L;

    /** Hard cap on the number of section uploads allowed per frame. */
    public static final int MAX_SECTIONS_PER_FRAME = 8;

    private static final AtomicLong TOTAL_UPLOADS = new AtomicLong();
    private static final AtomicLong YIELDED_FRAMES = new AtomicLong();
    private static volatile boolean enabled = true;

    private ThoriumChunkUploadPacer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

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

    public static long getTotalUploads() {
        return TOTAL_UPLOADS.get();
    }

    public static long getYieldedFrames() {
        return YIELDED_FRAMES.get();
    }
}

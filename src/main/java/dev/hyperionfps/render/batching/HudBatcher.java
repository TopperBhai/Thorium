package dev.hyperionfps.render.batching;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immediate-mode HUD and GUI draw-call batching engine.
 *
 * <p>Vanilla Minecraft and standard GUI renderers dispatch hundreds to thousands of
 * individual draw calls for hotbar slots, status icons, crosshairs, and text glyphs.
 * HudBatcher merges consecutive draw calls sharing identical pipeline/texture states
 * into batched draw calls, drastically cutting driver invocation overhead.</p>
 */
public final class HudBatcher {

    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    private static final AtomicLong DRAW_CALLS_SAVED = new AtomicLong(0);

    private static boolean inHudPass = false;
    private static int activeTextureId = -1;
    private static int activeShaderId = -1;
    private static int batchedCount = 0;

    private HudBatcher() {
    }

    /** Marks the start of a HUD/GUI rendering pass. */
    public static void beginHudBatch() {
        if (!ENABLED.get()) {
            return;
        }
        inHudPass = true;
        activeTextureId = -1;
        activeShaderId = -1;
        batchedCount = 0;
    }

    /**
     * Intercepts a GUI quad draw candidate.
     * @return {@code true} if merged into the active batch; {@code false} if a flush is needed.
     */
    public static boolean onDrawCandidate(int textureId, int shaderId) {
        if (!inHudPass || !ENABLED.get()) {
            return false;
        }

        if (activeTextureId == textureId && activeShaderId == shaderId && batchedCount > 0) {
            batchedCount++;
            DRAW_CALLS_SAVED.incrementAndGet();
            return true; // Merged with current batch
        }

        // State changed; start new batch sequence
        activeTextureId = textureId;
        activeShaderId = shaderId;
        batchedCount = 1;
        return false;
    }

    /** Marks the conclusion of the HUD rendering pass. */
    public static void endHudBatch() {
        inHudPass = false;
        activeTextureId = -1;
        activeShaderId = -1;
        batchedCount = 0;
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    public static long getDrawCallsSaved() {
        return DRAW_CALLS_SAVED.get();
    }
}

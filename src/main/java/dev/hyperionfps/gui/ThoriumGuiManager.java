package dev.hyperionfps.gui;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.render.batching.HudBatcher;
import dev.hyperionfps.render.state.GlStateTracker;
import net.minecraft.network.chat.Component;

/**
 * Unified GUI and performance governor manager for Thorium Optimization.
 *
 * <p>Handles Extreme FPS Mode toggles, keybind listeners (Shift + F10),
 * and live performance telemetry formatting.</p>
 */
public final class ThoriumGuiManager {

    private static final java.util.concurrent.atomic.AtomicBoolean HUD_ENABLED = new java.util.concurrent.atomic.AtomicBoolean(true);

    private ThoriumGuiManager() {
    }

    public static boolean isHudEnabled() {
        return HUD_ENABLED.get();
    }

    public static void setHudEnabled(boolean enabled) {
        HUD_ENABLED.set(enabled);
    }

    /** Toggles Extreme FPS Mode on or off and returns the notification component. */
    public static Component toggleExtremeFpsMode() {
        boolean newState = !AsyncOcclusionCuller.isExtremeFpsMode();
        AsyncOcclusionCuller.setExtremeFpsMode(newState);

        if (newState) {
            return Component.literal("§2[§aThorium§2] §aExtreme FPS Mode: ON §7(Fog OFF, Weather OFF, Max Culling)");
        } else {
            return Component.literal("§2[§aThorium§2] §cExtreme FPS Mode: OFF §7(Vanilla Visuals Restored)");
        }
    }

    /** Formats a full diagnostic performance summary for overlays and debug screens. */
    public static String getTelemetrySummary() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemMiB = runtime.maxMemory() / (1024 * 1024);

        return String.format(
                "Thorium (Th-90) | Memory: %d/%d MiB | Extreme Mode: %s | Culling: %s (Scale: %.2fx) | GL Skips: %d | HUD Batched: %d",
                usedMemMiB,
                maxMemMiB,
                AsyncOcclusionCuller.isExtremeFpsMode() ? "ON" : "OFF",
                AsyncOcclusionCuller.isEnabled() ? "ACTIVE" : "OFF",
                AsyncOcclusionCuller.getDynamicDistanceScale(),
                GlStateTracker.getRedundantTextureBindsAvoided() + GlStateTracker.getRedundantProgramsAvoided(),
                HudBatcher.getDrawCallsSaved()
        );
    }
}

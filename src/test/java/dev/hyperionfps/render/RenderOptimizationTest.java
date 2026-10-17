package dev.hyperionfps.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.hyperionfps.gui.ThoriumGuiManager;
import dev.hyperionfps.render.batching.HudBatcher;
import dev.hyperionfps.render.state.GlStateTracker;

class RenderOptimizationTest {

    @Test
    void glStateTrackerFiltersRedundantCalls() {
        GlStateTracker.setActiveTextureUnit(0);

        // First bind goes through
        assertTrue(GlStateTracker.shouldBindTexture(42));
        // Consecutive bind to same texture on same unit is skipped
        assertFalse(GlStateTracker.shouldBindTexture(42));
        // Bind to different texture goes through
        assertTrue(GlStateTracker.shouldBindTexture(43));

        // Program switch
        assertTrue(GlStateTracker.shouldUseProgram(101));
        assertFalse(GlStateTracker.shouldUseProgram(101));
        assertTrue(GlStateTracker.shouldUseProgram(102));

        // Redundant state counters incremented
        assertTrue(GlStateTracker.getRedundantTextureBindsAvoided() >= 1);
        assertTrue(GlStateTracker.getRedundantProgramsAvoided() >= 1);
    }

    @Test
    void hudBatcherMergesMatchingDraws() {
        HudBatcher.setEnabled(true);
        HudBatcher.beginHudBatch();

        // First draw establishes the state
        assertFalse(HudBatcher.onDrawCandidate(10, 20));

        // Consecutive draws with same texture & shader are merged into batch
        assertTrue(HudBatcher.onDrawCandidate(10, 20));
        assertTrue(HudBatcher.onDrawCandidate(10, 20));
        assertTrue(HudBatcher.onDrawCandidate(10, 20));

        // State change forces a flush
        assertFalse(HudBatcher.onDrawCandidate(11, 20));

        HudBatcher.endHudBatch();
        assertTrue(HudBatcher.getDrawCallsSaved() >= 3);
    }

    @Test
    void thoriumGuiManagerTelemetryFormatting() {
        String telemetry = ThoriumGuiManager.getTelemetrySummary();
        assertNotNull(telemetry);
        assertTrue(telemetry.contains("Thorium"));
        assertTrue(telemetry.contains("Memory:"));
        assertTrue(telemetry.contains("Extreme Mode:"));
    }
}

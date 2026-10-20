package dev.hyperionfps.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThoriumCompatibilityTest {

    @AfterEach
    void tearDown() {
        ThoriumCompatibility.setFastMathEnabled(false);
        ThoriumCompatibility.setAutoYieldEnabled(true);
    }

    @Test
    void fastMathToggleIsHonoured() {
        ThoriumCompatibility.setFastMathEnabled(true);
        assertTrue(ThoriumCompatibility.isFastMathEnabled());

        ThoriumCompatibility.setFastMathEnabled(false);
        assertFalse(ThoriumCompatibility.isFastMathEnabled());
    }

    @Test
    void detectionOutsideAFabricLoaderReportsNothing() {
        // The unit tests run without a Fabric loader, so nothing can be detected.
        ThoriumCompatibility.detect(true);
        assertFalse(ThoriumCompatibility.hasCompetingRenderer());
        assertFalse(ThoriumCompatibility.hasShaderPack());
    }

    @Test
    void autoYieldFlagIsReadable() {
        ThoriumCompatibility.setAutoYieldEnabled(false);
        assertFalse(ThoriumCompatibility.isAutoYieldEnabled());
        ThoriumCompatibility.setAutoYieldEnabled(true);
        assertTrue(ThoriumCompatibility.isAutoYieldEnabled());
    }

    @Test
    void yieldIsNotAppliedWithoutACompetingRenderer() {
        ThoriumCompatibility.detect(true);
        ThoriumCompatibility.applyYieldToCompetingRenderer();
        // Nothing is detected outside a game, so the culling stays enabled.
        assertFalse(ThoriumCompatibility.isYieldApplied());
        assertTrue(dev.hyperionfps.culling.AsyncOcclusionCuller.isEnabled());
    }

    @Test
    void subsystemReportListsEverySubsystem() {
        var entries = ThoriumSubsystemReport.collect();
        assertFalse(entries.isEmpty());
        for (ThoriumSubsystemReport.Entry entry : entries) {
            assertNotNull(entry.feature());
            assertNotNull(entry.status());
        }
    }

    @Test
    void subsystemReportMarksTheUnwiredSubsystems() {
        String report = ThoriumSubsystemReport.format();
        assertTrue(report.contains("UNWIRED"), "the report must call out the unwired subsystems");
        assertTrue(report.contains("frame-context"), "the report must contain the frame context");
        assertTrue(report.contains("gpu-entity-instancing"),
                "the report must contain the GPU entity instancing entry");
    }
}

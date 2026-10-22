package dev.hyperionfps.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;

class ThoriumReflexControllerTest {

    @BeforeEach
    void setUp() {
        ThoriumReflexController.resetForTests();
        ThoriumFeatureToggles.resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        ThoriumReflexController.resetForTests();
        ThoriumFeatureToggles.resetToDefaults();
    }

    // ------------------------------------------------------------------
    // The decision table
    // ------------------------------------------------------------------

    @Test
    void samplesOnlyWhenEverythingLinesUp() {
        assertTrue(ThoriumReflexController.decide(true, true, true));
    }

    @Test
    void doesNotSampleWhenTheFeatureIsOff() {
        assertFalse(ThoriumReflexController.decide(false, true, true));
    }

    @Test
    void doesNotSampleWithoutAPlatformPump() {
        assertFalse(ThoriumReflexController.decide(true, false, true));
    }

    @Test
    void doesNotSampleWhileTheWindowIsUnfocused() {
        assertFalse(ThoriumReflexController.decide(true, true, false));
    }

    @Test
    void shouldSampleFollowsTheFeatureSwitch() {
        ThoriumReflexController.installPump(() -> { });
        assertTrue(ThoriumReflexController.shouldSample(true));

        ThoriumFeatureToggles.setReflexEnabled(false);
        assertFalse(ThoriumReflexController.shouldSample(true));
    }

    @Test
    void shouldSampleNeedsAFocusedWindow() {
        ThoriumReflexController.installPump(() -> { });
        assertFalse(ThoriumReflexController.shouldSample(false));
    }

    @Test
    void shouldSampleNeedsAPlatformPump() {
        assertFalse(ThoriumReflexController.shouldSample(true));
    }

    // ------------------------------------------------------------------
    // The pump
    // ------------------------------------------------------------------

    @Test
    void pumpEventsIsANoOpWithoutAPlatformPump() {
        assertFalse(ThoriumReflexController.pumpEvents());
    }

    @Test
    void pumpEventsDrainsTheQueue() {
        AtomicInteger drains = new AtomicInteger();
        ThoriumReflexController.installPump(drains::incrementAndGet);

        assertTrue(ThoriumReflexController.pumpEvents());
        assertEquals(1, drains.get());
    }

    @Test
    void pumpEventsSurvivesAFailingPlatform() {
        ThoriumReflexController.installPump(() -> {
            throw new IllegalStateException("no window");
        });

        assertFalse(ThoriumReflexController.pumpEvents());
    }

    @Test
    void installingNullRemovesThePlatformPump() {
        ThoriumReflexController.installPump(() -> { });
        assertTrue(ThoriumReflexController.isPumpInstalled());

        ThoriumReflexController.installPump(null);
        assertFalse(ThoriumReflexController.isPumpInstalled());
    }

    // ------------------------------------------------------------------
    // The accounting
    // ------------------------------------------------------------------

    @Test
    void everySampleIsCounted() {
        ThoriumReflexController.recordLateSample(0.0, 0.0);
        ThoriumReflexController.recordLateSample(0.0, 0.0);

        assertEquals(2, ThoriumReflexController.getLateSamples());
    }

    @Test
    void aSampleWithoutMovementIsNotCountedAsFreshInput() {
        ThoriumReflexController.recordLateSample(0.0, 0.0);

        assertEquals(1, ThoriumReflexController.getLateSamples());
        assertEquals(0, ThoriumReflexController.getFreshInputFrames());
        assertEquals(0.0, ThoriumReflexController.getRecoveredPixels(), 1.0E-9);
    }

    @Test
    void subPixelNoiseIsNotCountedAsFreshInput() {
        ThoriumReflexController.recordLateSample(
                ThoriumReflexController.FRESH_INPUT_EPSILON / 2.0, 0.0);

        assertEquals(1, ThoriumReflexController.getLateSamples());
        assertEquals(0, ThoriumReflexController.getFreshInputFrames());
    }

    @Test
    void recoveredMovementIsAccumulated() {
        ThoriumReflexController.recordLateSample(3.0, 4.0);
        ThoriumReflexController.recordLateSample(-1.0, 0.0);

        assertEquals(2, ThoriumReflexController.getFreshInputFrames());
        assertEquals(8.0, ThoriumReflexController.getRecoveredPixels(), 1.0E-6);
        assertEquals(1.0, ThoriumReflexController.getLastRecoveredPixels(), 1.0E-9);
    }

    @Test
    void describeActivityNamesWhatHappened() {
        ThoriumReflexController.recordLateSample(2.0, 0.0);

        String description = ThoriumReflexController.describeActivity();
        assertTrue(description.contains("1 late samples"), description);
        assertTrue(description.contains("1 with fresh input"), description);
        assertTrue(description.contains("2.0px"), description);
    }

    @Test
    void resetClearsTheCounters() {
        ThoriumReflexController.installPump(() -> { });
        ThoriumReflexController.recordLateSample(5.0, 5.0);

        ThoriumReflexController.resetForTests();

        assertEquals(0, ThoriumReflexController.getLateSamples());
        assertEquals(0, ThoriumReflexController.getFreshInputFrames());
        assertEquals(0.0, ThoriumReflexController.getRecoveredPixels(), 1.0E-9);
        assertEquals(0.0, ThoriumReflexController.getLastRecoveredPixels(), 1.0E-9);
        assertFalse(ThoriumReflexController.isPumpInstalled());
    }

    // ------------------------------------------------------------------
    // The platform pump
    // ------------------------------------------------------------------

    @Test
    void everyNodeShipsAPlatformPump() throws Exception {
        // The controller looks the drain up by name, because the shared source may
        // not mention GLFW or SDL. This test proves that the name resolves on this
        // node and that the class really is a drain - a node that lost its
        // implementation would fail here instead of silently losing the feature.
        Class<?> impl = Class.forName("dev.hyperionfps.input.ReflexEventPumpImpl");
        assertTrue(ReflexEventPump.class.isAssignableFrom(impl),
                impl.getName() + " must implement " + ReflexEventPump.class.getName());
    }
}

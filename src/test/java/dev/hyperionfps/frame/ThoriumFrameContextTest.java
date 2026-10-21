package dev.hyperionfps.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.culling.AsyncOcclusionCuller;

class ThoriumFrameContextTest {

    @BeforeEach
    void setUp() {
        AsyncOcclusionCuller.setEnabled(true);
        AsyncOcclusionCuller.setExtremeFpsMode(false);
    }

    @AfterEach
    void tearDown() {
        ThoriumFramePacer.setTargetFps(0);
        ThoriumFramePacer.setEnabled(true);
    }

    @Test
    void worldFramePublishesTheCameraContext() {
        ThoriumFrameContext.beginWorldFrame(12.5, -30.0, 64.25, null, null);

        assertEquals(12.5, AsyncOcclusionCuller.getCameraX(), 1.0E-6);
        assertEquals(-30.0, AsyncOcclusionCuller.getCameraY(), 1.0E-6);
        assertEquals(64.25, AsyncOcclusionCuller.getCameraZ(), 1.0E-6);
    }

    @Test
    void clientFrameIsCounted() {
        long before = ThoriumFrameContext.clientFrameCount();
        ThoriumFrameContext.beginClientFrame(false, true);
        assertEquals(before + 1, ThoriumFrameContext.clientFrameCount());
    }

    @Test
    void distanceScaleShrinksOnSlowFramesAndRecoversOnFastFrames() {
        // Start from a known state without assuming what a previous test left behind.
        AsyncOcclusionCuller.updateDynamicDistanceScale(30_000_000L);
        AsyncOcclusionCuller.updateDynamicDistanceScale(30_000_000L);
        double before = AsyncOcclusionCuller.getDynamicDistanceScale();

        // A single slow frame must not collapse the scale - it is rate limited to one
        // step per frame, which is what the per-entity call used to break.
        AsyncOcclusionCuller.updateDynamicDistanceScale(30_000_000L);
        double afterOneSlowFrame = AsyncOcclusionCuller.getDynamicDistanceScale();
        assertTrue(afterOneSlowFrame <= before, "a slow frame must not grow the distance scale");
        assertTrue(afterOneSlowFrame >= before - 0.05 - 1.0E-9,
                "a single slow frame must not collapse the scale");

        // Many slow frames walk the scale down to the floor, never below it.
        for (int i = 0; i < 100; i++) {
            AsyncOcclusionCuller.updateDynamicDistanceScale(30_000_000L);
        }
        assertEquals(0.35, AsyncOcclusionCuller.getDynamicDistanceScale(), 1.0E-6);

        // Fast frames walk it back up to the ceiling.
        for (int i = 0; i < 100; i++) {
            AsyncOcclusionCuller.updateDynamicDistanceScale(1_000_000L);
        }
        assertEquals(1.0, AsyncOcclusionCuller.getDynamicDistanceScale(), 1.0E-6);
    }

    @Test
    void targetFpsSelectsUncappedForNonPositiveAndExtremeValues() {
        ThoriumFramePacer.setTargetFps(0);
        assertEquals(0, ThoriumFramePacer.getTargetFps());
        assertEquals(0L, ThoriumFramePacer.getTargetFrameTimeNanos());

        ThoriumFramePacer.setTargetFps(-10);
        assertEquals(0, ThoriumFramePacer.getTargetFps());

        ThoriumFramePacer.setTargetFps(1000);
        assertEquals(0, ThoriumFramePacer.getTargetFps());
    }

    @Test
    void targetFpsConvertsIntoAFramePeriod() {
        ThoriumFramePacer.setTargetFps(60);
        assertEquals(60, ThoriumFramePacer.getTargetFps());
        assertEquals(16_666_666L, ThoriumFramePacer.getTargetFrameTimeNanos(), 2.0);

        ThoriumFramePacer.setTargetFps(144);
        assertEquals(144, ThoriumFramePacer.getTargetFps());
        assertEquals(6_944_444L, ThoriumFramePacer.getTargetFrameTimeNanos(), 2.0);
    }

    @Test
    void uncappedPacerReturnsImmediately() {
        ThoriumFramePacer.setTargetFps(0);
        long before = System.nanoTime();
        ThoriumFramePacer.paceFrame();
        long elapsed = System.nanoTime() - before;
        assertTrue(elapsed < 5_000_000L, "an uncapped pacer must not sleep");
    }

    @Test
    void disabledPacerReturnsImmediately() {
        ThoriumFramePacer.setTargetFps(240);
        ThoriumFramePacer.setEnabled(false);
        long before = System.nanoTime();
        ThoriumFramePacer.paceFrame();
        long elapsed = System.nanoTime() - before;
        assertTrue(elapsed < 5_000_000L, "a disabled pacer must not sleep");
        ThoriumFramePacer.setEnabled(true);
    }

    @Test
    void pacerAnchorsWithoutSleepingOnTheFirstFrame() {
        ThoriumFramePacer.setTargetFps(30);
        long before = System.nanoTime();
        ThoriumFramePacer.paceFrame();
        long elapsed = System.nanoTime() - before;
        assertTrue(elapsed < 5_000_000L, "the first frame after enabling must not sleep");
    }

    @Test
    void framePacerToggleIsHonoured() {
        assertTrue(ThoriumFramePacer.isEnabled());
        ThoriumFramePacer.setEnabled(false);
        assertFalse(ThoriumFramePacer.isEnabled());
        ThoriumFramePacer.setEnabled(true);
        assertTrue(ThoriumFramePacer.isEnabled());
    }
}

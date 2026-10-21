package dev.hyperionfps.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveDistanceTest {

    private AdaptiveDistanceController controller;
    private AtomicInteger reportedDistance;
    private long now;

    @BeforeEach
    void setUp() {
        controller = AdaptiveDistanceController.getInstance();
        controller.setEnabled(true);
        controller.setTargetFps(60);
        controller.setMinDistance(4);
        controller.setMaxDistance(32);
        controller.setCurrentDistance(12);
        controller.reset();
        reportedDistance = new AtomicInteger(-1);
        controller.setDistanceConsumer(reportedDistance::set);
        now = System.currentTimeMillis();
    }

    @Test
    void testInitialConfiguration() {
        assertEquals(60, controller.getTargetFps());
        assertEquals(4, controller.getMinDistance());
        assertEquals(32, controller.getMaxDistance());
        assertEquals(12, controller.getCurrentDistance());
        assertTrue(controller.isEnabled());
    }

    @Test
    void testFrameSmoothing() {
        // Feed 16.6ms frames (60 FPS)
        for (int i = 0; i < 50; i++) {
            controller.onFrame(16_666_666L);
        }
        assertTrue(Math.abs(controller.getSmoothedFps() - 60.0) < 1.0);

        // Feed heavy 50ms frames (20 FPS)
        for (int i = 0; i < 20; i++) {
            controller.onFrame(50_000_000L);
        }
        // Smoothed FPS should smoothly decline towards 20
        assertTrue(controller.getSmoothedFps() < 40.0);
    }

    @Test
    void testStepDownNeedsTwoConsecutiveLowSamples() {
        controller.setSmoothedFps(30.0);

        // First low sample only arms the governor.
        assertFalse(controller.checkAndAdjust(now + 3000L));
        assertEquals(12, controller.getCurrentDistance());

        // The second consecutive low sample applies the change.
        assertTrue(controller.checkAndAdjust(now + 6000L));
        assertEquals(11, controller.getCurrentDistance());
        assertEquals(11, reportedDistance.get());
        assertEquals(1, controller.getAdjustmentsMade());
    }

    @Test
    void testHighFramerateNeverRaisesTheDistance() {
        controller.setSmoothedFps(240.0);

        // A fast machine is the normal case: the governor must not chase more chunks,
        // because every distance change reloads the whole chunk cache.
        for (int i = 1; i <= 10; i++) {
            assertFalse(controller.checkAndAdjust(now + (i * 3000L)));
        }
        assertEquals(12, controller.getCurrentDistance());
        assertEquals(-1, reportedDistance.get());
        assertEquals(0, controller.getAdjustmentsMade());
    }

    @Test
    void testDistanceIsNeverLoweredBelowTheMinimum() {
        controller.setCurrentDistance(4);
        controller.setSmoothedFps(20.0);

        assertFalse(controller.checkAndAdjust(now + 3000L));
        assertFalse(controller.checkAndAdjust(now + 6000L));
        assertEquals(4, controller.getCurrentDistance());
        assertEquals(-1, reportedDistance.get());
    }

    @Test
    void testSettleWindowBlocksASecondChange() {
        controller.setSmoothedFps(25.0);

        // Two low samples: the change is applied.
        controller.checkAndAdjust(now + 3000L);
        assertTrue(controller.checkAndAdjust(now + 6000L));
        assertEquals(11, controller.getCurrentDistance());

        // The world needs time to reload: no further change inside the settle window.
        assertFalse(controller.checkAndAdjust(now + 9000L));
        assertFalse(controller.checkAndAdjust(now + 12000L));
        assertEquals(11, controller.getCurrentDistance());
        assertEquals(1, controller.getAdjustmentsMade());

        // After the settle window the governor may act again - but it still needs two
        // consecutive low samples, because the blocked evaluations reset the counter.
        assertFalse(controller.checkAndAdjust(now + 33000L));
        assertFalse(controller.checkAndAdjust(now + 36000L));
        assertTrue(controller.checkAndAdjust(now + 39000L));
        assertEquals(10, controller.getCurrentDistance());
        assertEquals(2, controller.getAdjustmentsMade());
    }

    @Test
    void testEvaluationIntervalIsRespected() {
        controller.setSmoothedFps(25.0);

        // Too soon after the last evaluation: nothing happens.
        assertFalse(controller.checkAndAdjust(now + 500L));
        assertEquals(12, controller.getCurrentDistance());
    }

    @Test
    void testDisabledGovernorDoesNothing() {
        controller.setEnabled(false);
        controller.setSmoothedFps(10.0);

        assertFalse(controller.checkAndAdjust(now + 3000L));
        assertFalse(controller.checkAndAdjust(now + 6000L));
        assertEquals(12, controller.getCurrentDistance());
        assertEquals(-1, reportedDistance.get());
    }
}

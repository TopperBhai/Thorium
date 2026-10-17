package dev.hyperionfps.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveDistanceTest {

    private AdaptiveDistanceController controller;

    @BeforeEach
    void setUp() {
        controller = AdaptiveDistanceController.getInstance();
        controller.setEnabled(true);
        controller.setTargetFps(60);
        controller.setMinDistance(4);
        controller.setMaxDistance(32);
        controller.setCurrentDistance(12);
        controller.reset();
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
    void testAdaptiveDistanceStepDownOnLowFps() {
        AtomicInteger reportedDistance = new AtomicInteger(-1);
        controller.setDistanceConsumer(reportedDistance::set);

        // Simulate severely low FPS (e.g. 30 FPS when target is 60)
        controller.setSmoothedFps(30.0);

        long now = System.currentTimeMillis();
        // Cooldown initially prevents immediate adjustment if within interval
        // Wait or simulate time passage: now + 3000ms
        boolean adjusted = controller.checkAndAdjust(now + 3000L);
        assertTrue(adjusted);
        assertEquals(11, controller.getCurrentDistance());
        assertEquals(11, reportedDistance.get());
        assertEquals(1, controller.getAdjustmentsMade());
    }

    @Test
    void testAdaptiveDistanceStepUpOnHighFps() {
        AtomicInteger reportedDistance = new AtomicInteger(-1);
        controller.setDistanceConsumer(reportedDistance::set);

        // Simulate high FPS headroom (e.g. 100 FPS when target is 60)
        controller.setSmoothedFps(100.0);

        long now = System.currentTimeMillis();
        boolean adjusted = controller.checkAndAdjust(now + 3000L);
        assertTrue(adjusted);
        assertEquals(13, controller.getCurrentDistance());
        assertEquals(13, reportedDistance.get());
        assertEquals(1, controller.getAdjustmentsMade());
    }

    @Test
    void testDistanceClampedToMinAndMax() {
        controller.setCurrentDistance(4);
        controller.setSmoothedFps(20.0);

        // Should not drop below minDistance (4)
        boolean adjusted = controller.checkAndAdjust(System.currentTimeMillis() + 3000L);
        assertFalse(adjusted);
        assertEquals(4, controller.getCurrentDistance());

        controller.setCurrentDistance(32);
        controller.setSmoothedFps(200.0);

        // Should not rise above maxDistance (32)
        adjusted = controller.checkAndAdjust(System.currentTimeMillis() + 6000L);
        assertFalse(adjusted);
        assertEquals(32, controller.getCurrentDistance());
    }

    @Test
    void testCooldownPreventsExcessiveHitching() {
        controller.setSmoothedFps(25.0);
        long now = System.currentTimeMillis();

        // First adjust succeeds after interval
        assertTrue(controller.checkAndAdjust(now + 2500L));
        assertEquals(11, controller.getCurrentDistance());

        // Immediately checking 500ms later must be blocked by cooldown
        assertFalse(controller.checkAndAdjust(now + 3000L));
        assertEquals(11, controller.getCurrentDistance());
    }
}

package dev.hyperionfps.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderDistanceGovernorTest {

    @BeforeEach
    void setUp() {
        AdaptiveDistanceController.getInstance().setEnabled(true);
        AdaptiveDistanceController.getInstance().reset();
    }

    @Test
    void distanceIsUnavailableOutsideAGame() {
        // There is no Minecraft instance in a unit test, so the governor must report
        // "unknown" instead of throwing.
        assertEquals(-1, RenderDistanceGovernor.currentDistance());
    }

    @Test
    void userOverrideIsNotActiveByDefault() {
        assertFalse(RenderDistanceGovernor.isUserOverrideActive());
    }

    @Test
    void installRegistersAConsumer() {
        RenderDistanceGovernor.install();
        // The consumer must be present, otherwise the governor could never apply a
        // distance. It is invoked with a distance and must never throw.
        AdaptiveDistanceController controller = AdaptiveDistanceController.getInstance();
        controller.setCurrentDistance(12);
        controller.checkAndAdjust(System.currentTimeMillis() + 10_000L);
    }
}

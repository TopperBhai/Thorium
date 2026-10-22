package dev.hyperionfps.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.chunk.ElytraFlightSmoother.Priority;

class ElytraFlightSmootherTest {

    @BeforeEach
    void setUp() {
        ElytraFlightSmoother.resetForTests();
    }

    @AfterEach
    void tearDown() {
        ElytraFlightSmoother.resetForTests();
    }

    // ------------------------------------------------------------------
    // The speed threshold
    // ------------------------------------------------------------------

    @Test
    void theSpeedThresholdIsStrictlyFifteenMetresPerSecond() {
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 15.0);
        assertFalse(ElytraFlightSmoother.isFastFlight(), "exactly 15 m/s is not faster than 15 m/s");

        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 15.01);
        assertTrue(ElytraFlightSmoother.isFastFlight());

        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 0.0);
        assertFalse(ElytraFlightSmoother.isFastFlight());
    }

    @Test
    void theSpeedIsTheLengthOfTheVelocityVector() {
        // 3-4-5 triangle: 30 m/s in total.
        ElytraFlightSmoother.publishVelocity(18.0, 0.0, 24.0);
        assertEquals(30.0, ElytraFlightSmoother.speed(), 1.0E-9);
        assertTrue(ElytraFlightSmoother.isFastFlight());
    }

    @Test
    void aClearedVelocityEndsTheFlight() {
        ElytraFlightSmoother.publishVelocity(20.0, 0.0, 0.0);
        assertTrue(ElytraFlightSmoother.isFastFlight());

        ElytraFlightSmoother.clearVelocity();
        assertFalse(ElytraFlightSmoother.isFastFlight());
        assertEquals(0.0, ElytraFlightSmoother.speed(), 1.0E-9);
    }

    // ------------------------------------------------------------------
    // The forward cone
    // ------------------------------------------------------------------

    @Test
    void aSectionStraightAheadIsForward() {
        // Flying towards +X, the section is 40 blocks ahead.
        assertEquals(Priority.FORWARD, ElytraFlightSmoother.classify(40.0, 0.0, 0.0, 20.0, 0.0, 0.0));
    }

    @Test
    void aSectionDirectlyBehindIsBehind() {
        assertEquals(Priority.BEHIND, ElytraFlightSmoother.classify(-40.0, 0.0, 0.0, 20.0, 0.0, 0.0));
    }

    @Test
    void aSectionAtRightAnglesIsNeutral() {
        assertEquals(Priority.NEUTRAL, ElytraFlightSmoother.classify(0.0, 0.0, 40.0, 20.0, 0.0, 0.0));
    }

    @Test
    void theConeEdgeIsFortyFiveDegrees() {
        // 45 degrees off the flight vector: the cosine is 0.7071, so it is still
        // inside the cone. 60 degrees is outside it.
        double at45 = Math.cos(Math.PI / 4.0) * 40.0;
        double side = Math.sin(Math.PI / 4.0) * 40.0;
        assertEquals(Priority.FORWARD,
                ElytraFlightSmoother.classify(at45, 0.0, side, 20.0, 0.0, 0.0));

        double at60 = Math.cos(Math.PI / 3.0) * 40.0;
        double side60 = Math.sin(Math.PI / 3.0) * 40.0;
        assertEquals(Priority.NEUTRAL,
                ElytraFlightSmoother.classify(at60, 0.0, side60, 20.0, 0.0, 0.0));
    }

    @Test
    void aDirectionWithoutALengthHasNoPriority() {
        assertEquals(Priority.NEUTRAL, ElytraFlightSmoother.classify(0.0, 0.0, 0.0, 20.0, 0.0, 0.0));
    }

    @Test
    void aFlightWithoutAVelocityHasNoPriority() {
        assertEquals(Priority.NEUTRAL, ElytraFlightSmoother.classify(40.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void aDescendingFlightCountsTheVerticalComponent() {
        // Flying down and forwards at 45 degrees: a section below and ahead is in
        // the cone, a section straight below is not.
        assertEquals(Priority.FORWARD,
                ElytraFlightSmoother.classify(20.0, -20.0, 0.0, 20.0, -20.0, 0.0));
        assertEquals(Priority.NEUTRAL,
                ElytraFlightSmoother.classify(0.0, -40.0, 0.0, 20.0, -20.0, 0.0));
    }

    // ------------------------------------------------------------------
    // The deferral rule
    // ------------------------------------------------------------------

    @Test
    void nothingIsDeferredWhileThePlayerIsSlow() {
        ElytraFlightSmoother.publishCamera(0.0, 0.0, 0.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 10.0);
        assertFalse(ElytraFlightSmoother.shouldDefer(0, 64, -40),
                "a section behind a slow player must not be deferred");
    }

    @Test
    void aSectionBehindAFastPlayerIsDeferred() {
        ElytraFlightSmoother.publishCamera(0.0, 64.0, 0.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);
        assertTrue(ElytraFlightSmoother.shouldDefer(0, 64, -40));
    }

    @Test
    void aSectionInFrontIsNeverDeferred() {
        ElytraFlightSmoother.publishCamera(0.0, 64.0, 0.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);
        assertFalse(ElytraFlightSmoother.shouldDefer(0, 64, 40));
    }

    @Test
    void aSectionRightNextToThePlayerIsNeverDeferred() {
        ElytraFlightSmoother.publishCamera(0.0, 64.0, 0.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);
        assertFalse(ElytraFlightSmoother.shouldDefer(0, 64, -4),
                "a hole in the world next to the player is never worth a frame of delay");
        assertFalse(ElytraFlightSmoother.shouldDefer(0, 64, -15),
                "the guard is 16 blocks, so 15 blocks is still too close");
        assertTrue(ElytraFlightSmoother.shouldDefer(0, 64, -16));
    }

    // ------------------------------------------------------------------
    // The published state
    // ------------------------------------------------------------------

    @Test
    void thePublishedCameraAndVelocityAreReadable() {
        ElytraFlightSmoother.publishCamera(1.5, -30.0, 64.25);
        ElytraFlightSmoother.publishVelocity(1.0, 2.0, 3.0);

        assertEquals(1.5, ElytraFlightSmoother.cameraX(), 1.0E-9);
        assertEquals(-30.0, ElytraFlightSmoother.cameraY(), 1.0E-9);
        assertEquals(64.25, ElytraFlightSmoother.cameraZ(), 1.0E-9);
        assertEquals(1.0, ElytraFlightSmoother.velocityX(), 1.0E-9);
        assertEquals(2.0, ElytraFlightSmoother.velocityY(), 1.0E-9);
        assertEquals(3.0, ElytraFlightSmoother.velocityZ(), 1.0E-9);
    }

    @Test
    void theForwardConeIsTestedAgainstThePublishedState() {
        ElytraFlightSmoother.publishCamera(0.0, 64.0, 0.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);

        assertTrue(ElytraFlightSmoother.isInForwardCone(0, 64, 40));
        assertFalse(ElytraFlightSmoother.isInForwardCone(0, 64, -40));
    }

    @Test
    void theMetricsCountWhatHappened() {
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 1.0);

        assertEquals(2L, ElytraFlightSmoother.getFastFlightFrames(),
                "only the two fast frames may be counted");
        assertTrue(ElytraFlightSmoother.describeActivity().contains("fast-flight frames"));
    }
}

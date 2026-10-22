package dev.hyperionfps.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.culling.AsyncOcclusionCuller;

/**
 * Tests for the mob farm and swarm optimizer.
 *
 * <p>The optimizer is a policy layer with no game state of its own: the species of
 * an entity is passed in as an opaque key, so the whole decision table can be
 * exercised here without a running Minecraft. Only the camera position is real, and
 * it is published through the culling engine.</p>
 */
class MobFarmOptimizerTest {

    /** A stand-in for the {@code EntityType} of a zombie. */
    private static final Object ZOMBIE = new Object();

    /** A stand-in for the {@code EntityType} of a cow. */
    private static final Object COW = new Object();

    @BeforeEach
    void setUp() {
        ThoriumFeatureToggles.setMobFarmEnabled(true);
        MobFarmOptimizer.resetMetrics();
        AsyncOcclusionCuller.publishCamera(0.0, 0.0, 0.0, null);
    }

    @AfterEach
    void tearDown() {
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
        MobFarmOptimizer.resetMetrics();
    }

    // ------------------------------------------------------------------
    // Feature wiring
    // ------------------------------------------------------------------

    @Test
    void installMarksTheFeatureAsWired() {
        ThoriumFeatureToggles.resetWiredForTests();
        assertFalse(ThoriumFeatureToggles.Feature.MOB_FARM.isWired());

        MobFarmOptimizer.install();

        assertTrue(ThoriumFeatureToggles.Feature.MOB_FARM.isWired());
    }

    @Test
    void theOptimizerFollowsTheFeatureSwitch() {
        ThoriumFeatureToggles.setMobFarmEnabled(false);
        assertFalse(MobFarmOptimizer.isEnabled());

        ThoriumFeatureToggles.setMobFarmEnabled(true);
        assertTrue(MobFarmOptimizer.isEnabled());
    }

    @Test
    void aDisabledOptimizerNeverThrottles() {
        ThoriumFeatureToggles.setMobFarmEnabled(false);
        observeZombies(20, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();

        assertFalse(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
    }

    // ------------------------------------------------------------------
    // The threshold
    // ------------------------------------------------------------------

    @Test
    void aClusterNeedsMoreThanFifteenEntitiesOfOneSpecies() {
        assertFalse(MobFarmOptimizer.isDenseCluster(0));
        assertFalse(MobFarmOptimizer.isDenseCluster(15));
        assertTrue(MobFarmOptimizer.isDenseCluster(16));
    }

    @Test
    void theGridCellIsFiveBlocksWide() {
        assertEquals(0, MobFarmOptimizer.cellCoordinate(0.0));
        assertEquals(0, MobFarmOptimizer.cellCoordinate(4.99));
        assertEquals(1, MobFarmOptimizer.cellCoordinate(5.0));
        assertEquals(1, MobFarmOptimizer.cellCoordinate(9.99));

        // Negative coordinates must fall into the cell below zero, not into zero.
        assertEquals(-1, MobFarmOptimizer.cellCoordinate(-0.01));
        assertEquals(-1, MobFarmOptimizer.cellCoordinate(-5.0));
        assertEquals(-2, MobFarmOptimizer.cellCoordinate(-5.01));
    }

    // ------------------------------------------------------------------
    // The exemption rule
    // ------------------------------------------------------------------

    @Test
    void thePlayerNamedLeashedAndRidingMobsAreExempt() {
        assertTrue(MobFarmOptimizer.isExemptEntity(true, false, false, false), "the player");
        assertTrue(MobFarmOptimizer.isExemptEntity(false, true, false, false), "a named mob");
        assertTrue(MobFarmOptimizer.isExemptEntity(false, false, true, false), "a leashed mob");
        assertTrue(MobFarmOptimizer.isExemptEntity(false, false, false, true), "a passenger");
        assertFalse(MobFarmOptimizer.isExemptEntity(false, false, false, false), "an ordinary mob");
    }

    // ------------------------------------------------------------------
    // The decision table
    // ------------------------------------------------------------------

    @Test
    void onlyADenseUnprotectedAndDistractedMobIsThrottled() {
        assertTrue(MobFarmOptimizer.decideThrottle(true, false, false, true));

        // Not a cluster.
        assertFalse(MobFarmOptimizer.decideThrottle(false, false, false, true));
        // The cell holds an exempt mob.
        assertFalse(MobFarmOptimizer.decideThrottle(true, true, false, true));
        // The player is looking at it.
        assertFalse(MobFarmOptimizer.decideThrottle(true, false, true, true));
        // This frame is one of the frames the animation is recomputed on.
        assertFalse(MobFarmOptimizer.decideThrottle(true, false, false, false));
    }

    @Test
    void theAnimationRunsOnOneFrameOutOfThree() {
        int animated = 0;
        for (int hash = 0; hash < 30; hash++) {
            if (MobFarmOptimizer.isAnimationFrame(hash)) {
                animated++;
            }
        }
        assertEquals(10, animated, "one frame out of three must animate");
    }

    // ------------------------------------------------------------------
    // The cluster map
    // ------------------------------------------------------------------

    @Test
    void aDenseClusterIsThrottledOutsideTheFocus() {
        observeZombies(20, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();

        assertTrue(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
        assertTrue(MobFarmOptimizer.getAnimationsSkipped() > 0);
    }

    @Test
    void fifteenMobsAreNotACluster() {
        observeZombies(15, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();

        assertFalse(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
    }

    @Test
    void twoSpeciesInOneCellAreNotACluster() {
        for (int i = 0; i < 10; i++) {
            MobFarmOptimizer.observe(ZOMBIE, 40.0 + (i % 5), 40.0, false);
            MobFarmOptimizer.observe(COW, 40.0 + (i % 5), 40.0, false);
        }
        MobFarmOptimizer.beginFrame();

        assertFalse(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
        assertFalse(throttledAtAnyHash(COW, 40.0, 40.0, 40.0));
    }

    @Test
    void twoCellsAreNotACluster() {
        for (int i = 0; i < 10; i++) {
            MobFarmOptimizer.observe(ZOMBIE, 40.0 + (i % 5), 40.0, false);
            MobFarmOptimizer.observe(ZOMBIE, 200.0 + (i % 5), 40.0, false);
        }
        MobFarmOptimizer.beginFrame();

        assertFalse(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
        assertFalse(throttledAtAnyHash(ZOMBIE, 200.0, 40.0, 40.0));
    }

    @Test
    void anExemptMobProtectsItsWholeCell() {
        for (int i = 0; i < 20; i++) {
            // The first mob is exempt: it must not be counted, and it must stop the
            // whole cell from being throttled.
            MobFarmOptimizer.observe(ZOMBIE, 40.0 + (i % 5), 40.0, i == 0);
        }
        MobFarmOptimizer.beginFrame();

        assertFalse(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
    }

    @Test
    void aMobThePlayerIsLookingAtIsNeverThrottled() {
        // A dense cluster right next to the camera. All of them sit in the same
        // 5x5 cell, which is the cell the decisions are looked up in.
        for (int i = 0; i < 20; i++) {
            MobFarmOptimizer.observe(ZOMBIE, 2.0 + (i % 3), 0.0, false);
        }
        MobFarmOptimizer.beginFrame();

        // Two blocks away: inside the focus, so it keeps its full animation.
        assertFalse(throttledAtAnyHash(ZOMBIE, 2.0, 0.0, 0.0));
        // Four and a half blocks away: outside the focus, so it is throttled.
        assertTrue(throttledAtAnyHash(ZOMBIE, 4.5, 0.0, 0.0));
    }

    @Test
    void theMapIsOneFrameBehind() {
        observeZombies(20, 40.0, 40.0, false);

        // The cluster was recorded during this frame, so it is not published yet.
        assertFalse(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));

        MobFarmOptimizer.beginFrame();

        assertTrue(throttledAtAnyHash(ZOMBIE, 40.0, 40.0, 40.0));
    }

    @Test
    void aClusterIsCountedOnceAndNotAgainWhileItLasts() {
        observeZombies(20, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();
        assertEquals(1, MobFarmOptimizer.getClustersDetected());

        // The cluster is still there in the next frame, so it is not a new one.
        observeZombies(20, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();
        assertEquals(1, MobFarmOptimizer.getClustersDetected());
    }

    @Test
    void aDenseCellIsReportedToTheSoundGovernor() {
        observeZombies(20, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();

        assertTrue(MobFarmOptimizer.isDenseCell(40.0, 40.0));
        assertFalse(MobFarmOptimizer.isDenseCell(400.0, 400.0));
    }

    @Test
    void theSoundGovernorIsNotConsultedWhileTheOptimizerIsOff() {
        observeZombies(20, 40.0, 40.0, false);
        MobFarmOptimizer.beginFrame();
        assertTrue(MobFarmOptimizer.isDenseCell(40.0, 40.0));

        ThoriumFeatureToggles.setMobFarmEnabled(false);
        assertFalse(MobFarmOptimizer.isDenseCell(40.0, 40.0));
    }

    // ------------------------------------------------------------------
    // The acoustic dampening
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Records {@code count} zombies in the cell around the given position. */
    private static void observeZombies(int count, double x, double z, boolean exempt) {
        for (int i = 0; i < count; i++) {
            MobFarmOptimizer.observe(ZOMBIE, x + (i % 5), z, exempt);
        }
    }

    /** Whether the mob is throttled on any of the frames of the rate limit. */
    private static boolean throttledAtAnyHash(Object species, double x, double y, double z) {
        for (int hash = 0; hash < MobFarmOptimizer.ANIMATION_DIVISOR; hash++) {
            if (MobFarmOptimizer.shouldThrottle(species, x, y, z, hash)) {
                return true;
            }
        }
        return false;
    }
}

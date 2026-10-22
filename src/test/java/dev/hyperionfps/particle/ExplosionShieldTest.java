package dev.hyperionfps.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;

/**
 * Tests for the explosion and firework shield.
 *
 * <p>{@link ExplosionShield} is a pure policy engine - it holds no Minecraft type -
 * so the whole decision table, the burst rate limit and the per-cell redundancy
 * limit can be exercised here without a running Minecraft and without booting the
 * particle registry.</p>
 */
class ExplosionShieldTest {

    /** A fixed instant so the token buckets do not refill during a test. */
    private static final long BASE_TIME = 1_000_000L;

    @BeforeEach
    void setUp() {
        ThoriumFeatureToggles.setExplosionShieldEnabled(true);
        ExplosionShield.resetMetrics();
    }

    @AfterEach
    void tearDown() {
        ThoriumFeatureToggles.setExplosionShieldEnabled(true);
        ExplosionShield.resetMetrics();
    }

    // ------------------------------------------------------------------
    // Feature wiring
    // ------------------------------------------------------------------

    @Test
    void installMarksTheFeatureAsWired() {
        ThoriumFeatureToggles.resetWiredForTests();
        assertFalse(ThoriumFeatureToggles.Feature.EXPLOSION_SHIELD.isWired());

        ExplosionShield.install();

        assertTrue(ThoriumFeatureToggles.Feature.EXPLOSION_SHIELD.isWired());
    }

    @Test
    void shieldFollowsTheFeatureSwitch() {
        ThoriumFeatureToggles.setExplosionShieldEnabled(false);
        assertFalse(ExplosionShield.isEnabled());

        ThoriumFeatureToggles.setExplosionShieldEnabled(true);
        assertTrue(ExplosionShield.isEnabled());
    }

    // ------------------------------------------------------------------
    // The decision table
    // ------------------------------------------------------------------

    @Test
    void aProtectedParticleBeatsEveryLimit() {
        assertEquals(ExplosionShield.Decision.ADMIT_PRIORITY,
                ExplosionShield.decide(true, true, true));
    }

    @Test
    void aSaturatedCellIsCulledBeforeTheRateLimit() {
        assertEquals(ExplosionShield.Decision.CULL_REDUNDANT,
                ExplosionShield.decide(false, true, false));
        assertEquals(ExplosionShield.Decision.CULL_REDUNDANT,
                ExplosionShield.decide(false, true, true));
    }

    @Test
    void anExhaustedRateLimitCullsTheSurplus() {
        assertEquals(ExplosionShield.Decision.CULL_OVER_RATE,
                ExplosionShield.decide(false, false, true));
    }

    @Test
    void anUnconstrainedParticleIsAdmitted() {
        assertEquals(ExplosionShield.Decision.ADMIT,
                ExplosionShield.decide(false, false, false));
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    @Test
    void everyCategoryHasItsOwnBit() {
        assertEquals(0, ExplosionShield.Category.NONE.bit());
        assertEquals(1, ExplosionShield.Category.EXPLOSION.bit());
        assertEquals(2, ExplosionShield.Category.EXPLOSION_EMITTER.bit());
        assertEquals(4, ExplosionShield.Category.FIREWORK.bit());
        assertEquals(8, ExplosionShield.Category.CAMPFIRE_SMOKE.bit());
        assertEquals(16, ExplosionShield.Category.LARGE_SMOKE.bit());
    }

    @Test
    void anUntrackedCategoryIsNeverShielded() {
        assertEquals(ExplosionShield.Decision.ADMIT,
                ExplosionShield.evaluate(ExplosionShield.Category.NONE, 0.0, 64.0, 0.0, BASE_TIME));
        assertEquals(ExplosionShield.Decision.ADMIT,
                ExplosionShield.evaluate(null, 0.0, 64.0, 0.0, BASE_TIME));
    }

    // ------------------------------------------------------------------
    // The protected particles
    // ------------------------------------------------------------------

    @Test
    void theFirstParticleOfACategoryIsProtected() {
        ExplosionShield.Decision decision = ExplosionShield.evaluate(
                ExplosionShield.Category.EXPLOSION, 12.5, 64.0, -3.5, BASE_TIME);

        assertEquals(ExplosionShield.Decision.ADMIT_PRIORITY, decision);
    }

    @Test
    void theExplosionFlashAndThePrimaryPuffAreBothProtected() {
        // The flash and the puff are two different categories, so both of them are
        // the first of their kind in their cell and both survive.
        assertEquals(ExplosionShield.Decision.ADMIT_PRIORITY, ExplosionShield.evaluate(
                ExplosionShield.Category.EXPLOSION, 12.5, 64.0, -3.5, BASE_TIME));
        assertEquals(ExplosionShield.Decision.ADMIT_PRIORITY, ExplosionShield.evaluate(
                ExplosionShield.Category.EXPLOSION_EMITTER, 12.5, 64.0, -3.5, BASE_TIME));
    }

    @Test
    void theSecondParticleOfTheSameCategoryIsNotProtected() {
        ExplosionShield.evaluate(ExplosionShield.Category.LARGE_SMOKE, 4.0, 70.0, 4.0, BASE_TIME);

        ExplosionShield.Decision decision = ExplosionShield.evaluate(
                ExplosionShield.Category.LARGE_SMOKE, 4.0, 70.0, 4.0, BASE_TIME);

        assertEquals(ExplosionShield.Decision.ADMIT, decision);
    }

    @Test
    void aQuietCellStartsANewBurst() {
        ExplosionShield.evaluate(ExplosionShield.Category.EXPLOSION, 8.0, 64.0, 8.0, BASE_TIME);
        ExplosionShield.evaluate(ExplosionShield.Category.EXPLOSION, 8.0, 64.0, 8.0, BASE_TIME);

        // The cell goes quiet for longer than the idle window: the next explosion in
        // the same block gets its own flash.
        ExplosionShield.Decision decision = ExplosionShield.evaluate(
                ExplosionShield.Category.EXPLOSION, 8.0, 64.0, 8.0,
                BASE_TIME + ExplosionShield.CELL_IDLE_MS + 1L);

        assertEquals(ExplosionShield.Decision.ADMIT_PRIORITY, decision);
    }

    // ------------------------------------------------------------------
    // The per-cell redundancy limit
    // ------------------------------------------------------------------

    @Test
    void theCellLimitDropsTheOverlappingQuads() {
        // The first particle of the cell is the protected one; every further particle
        // of the same cell competes for the cell's own budget.
        assertEquals(ExplosionShield.Decision.ADMIT_PRIORITY, ExplosionShield.evaluate(
                ExplosionShield.Category.LARGE_SMOKE, 10.5, 64.0, 10.5, BASE_TIME));

        int admitted = 0;
        int redundant = 0;
        for (int i = 0; i < 100; i++) {
            ExplosionShield.Decision decision = ExplosionShield.evaluate(
                    ExplosionShield.Category.LARGE_SMOKE, 10.5, 64.0, 10.5, BASE_TIME);
            if (decision == ExplosionShield.Decision.ADMIT) {
                admitted++;
            } else if (decision == ExplosionShield.Decision.CULL_REDUNDANT) {
                redundant++;
            }
        }

        assertEquals(ExplosionShield.CELL_RATE_LIMIT, admitted);
        assertEquals(100 - ExplosionShield.CELL_RATE_LIMIT, redundant);
    }

    @Test
    void differentCellsHaveSeparateBudgets() {
        int admitted = 0;
        for (int cell = 0; cell < 2; cell++) {
            double x = cell * 8.0;
            ExplosionShield.evaluate(ExplosionShield.Category.LARGE_SMOKE, x, 64.0, 0.0, BASE_TIME);
            for (int i = 0; i < ExplosionShield.CELL_RATE_LIMIT; i++) {
                if (ExplosionShield.evaluate(ExplosionShield.Category.LARGE_SMOKE,
                        x, 64.0, 0.0, BASE_TIME) == ExplosionShield.Decision.ADMIT) {
                    admitted++;
                }
            }
        }

        assertEquals(ExplosionShield.CELL_RATE_LIMIT * 2, admitted);
    }

    // ------------------------------------------------------------------
    // The burst rate limit
    // ------------------------------------------------------------------

    @Test
    void theBurstRateLimitDropsTheSurplusAcrossManyCells() {
        // Two particles per cell in 400 different cells: the first of each cell is
        // protected and free, the second one has to pay for the burst rate.
        int admitted = 0;
        int overRate = 0;
        for (int cell = 0; cell < 400; cell++) {
            double x = cell * 4.0;
            for (int i = 0; i < 2; i++) {
                ExplosionShield.Decision decision = ExplosionShield.evaluate(
                        ExplosionShield.Category.EXPLOSION, x, 64.0, 0.0, BASE_TIME);
                if (decision == ExplosionShield.Decision.ADMIT
                        || decision == ExplosionShield.Decision.ADMIT_PRIORITY) {
                    admitted++;
                } else if (decision == ExplosionShield.Decision.CULL_OVER_RATE) {
                    overRate++;
                }
            }
        }

        // 400 protected particles plus the 250 ordinary ones the rate limit allows.
        assertEquals(400 + ExplosionShield.BURST_RATE_LIMIT, admitted);
        assertEquals(400 - ExplosionShield.BURST_RATE_LIMIT, overRate);
    }

    @Test
    void theBurstRateLimitIsARateAndNotACliff() {
        // The bucket refills continuously, so a client that keeps producing particles
        // still gets its share every second instead of being cut off entirely.
        int admitted = 0;
        for (int cell = 0; cell < 400; cell++) {
            double x = cell * 4.0;
            ExplosionShield.evaluate(ExplosionShield.Category.EXPLOSION, x, 64.0, 0.0, BASE_TIME);
            if (ExplosionShield.evaluate(ExplosionShield.Category.EXPLOSION,
                    x, 64.0, 0.0, BASE_TIME) == ExplosionShield.Decision.ADMIT) {
                admitted++;
            }
        }
        assertEquals(ExplosionShield.BURST_RATE_LIMIT, admitted);

        // One second later the bucket is full again.
        int admittedLater = 0;
        long later = BASE_TIME + 1000L;
        for (int cell = 0; cell < 400; cell++) {
            double x = cell * 4.0;
            ExplosionShield.evaluate(ExplosionShield.Category.EXPLOSION, x, 64.0, 0.0, later);
            if (ExplosionShield.evaluate(ExplosionShield.Category.EXPLOSION,
                    x, 64.0, 0.0, later) == ExplosionShield.Decision.ADMIT) {
                admittedLater++;
            }
        }
        assertEquals(ExplosionShield.BURST_RATE_LIMIT, admittedLater);
    }

    // ------------------------------------------------------------------
    // The cell geometry
    // ------------------------------------------------------------------

    @Test
    void theCellCoordinateIsTheBlockCoordinate() {
        assertEquals(1, ExplosionShield.cellCoordinate(1.9));
        assertEquals(0, ExplosionShield.cellCoordinate(0.0));
        assertEquals(-1, ExplosionShield.cellCoordinate(-0.1));
        assertEquals(-2, ExplosionShield.cellCoordinate(-1.5));
    }

    @Test
    void theCellKeyUsesTheBlockCoordinates() {
        long expected = (((long) 1 & 0x1FFFFF) << 42)
                | (((long) -1 & 0x1FFFFF) << 21)
                | (3 & 0x1FFFFF);

        assertEquals(expected, ExplosionShield.cellKey(1.9, -0.1, 3.2));
    }

    @Test
    void neighbouringPositionsShareACell() {
        assertEquals(ExplosionShield.cellKey(10.0, 64.0, 10.0),
                ExplosionShield.cellKey(10.99, 64.99, 10.01));
        assertFalse(ExplosionShield.cellKey(10.0, 64.0, 10.0)
                == ExplosionShield.cellKey(11.0, 64.0, 10.0));
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    @Test
    void theMetricsCountWhatTheShieldDid() {
        ExplosionShield.recordAdmitted();
        ExplosionShield.recordPriorityAdmitted();
        ExplosionShield.recordRedundantCull();
        ExplosionShield.recordOverRateCull();

        assertEquals(2, ExplosionShield.getAdmitted());
        assertEquals(1, ExplosionShield.getPriorityAdmitted());
        assertEquals(1, ExplosionShield.getCulledRedundant());
        assertEquals(1, ExplosionShield.getCulledOverRate());
        assertTrue(ExplosionShield.describeActivity().contains("2 admitted"));
    }

    @Test
    void resetMetricsClearsEverything() {
        ExplosionShield.recordAdmitted();
        ExplosionShield.resetMetrics();

        assertEquals(0, ExplosionShield.getAdmitted());
        assertEquals(0, ExplosionShield.getPriorityAdmitted());
        assertEquals(0, ExplosionShield.getCulledRedundant());
        assertEquals(0, ExplosionShield.getCulledOverRate());
    }
}

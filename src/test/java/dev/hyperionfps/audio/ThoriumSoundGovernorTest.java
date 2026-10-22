package dev.hyperionfps.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.anim.MobFarmOptimizer;
import dev.hyperionfps.core.ThoriumFeatureToggles;

/**
 * Tests for the sound engine governor and its mob farm dampening.
 *
 * <p>The governor is a policy layer: the rate limit and the mob farm policy are
 * expressed as pure functions of the number of events in the current window, so
 * they can be verified without a running Minecraft.</p>
 */
class ThoriumSoundGovernorTest {

    @BeforeEach
    void setUp() {
        ThoriumSoundGovernor.setEnabled(true);
        ThoriumFeatureToggles.setMobFarmEnabled(true);
        MobFarmOptimizer.resetMetrics();
    }

    @AfterEach
    void tearDown() {
        ThoriumSoundGovernor.setEnabled(true);
        ThoriumFeatureToggles.resetToDefaults();
        MobFarmOptimizer.resetMetrics();
    }

    @Test
    void theGeneralRateLimitIsUnchanged() {
        assertFalse(ThoriumSoundGovernor.exceedsCellLimit(4));
        assertTrue(ThoriumSoundGovernor.exceedsCellLimit(5));
    }

    @Test
    void aMobFarmIsDampenedAfterSixEventsAndDropsTheSurplus() {
        assertEquals(0, ThoriumSoundGovernor.clusterAction(1));
        assertEquals(0, ThoriumSoundGovernor.clusterAction(6));
        assertEquals(1, ThoriumSoundGovernor.clusterAction(7), "the seventh event is dampened");
        assertEquals(1, ThoriumSoundGovernor.clusterAction(12));
        assertEquals(2, ThoriumSoundGovernor.clusterAction(13), "the surplus is dropped");
    }

    @Test
    void theDampeningBudgetIsSixEventsPerTick() {
        assertEquals(6, ThoriumSoundGovernor.CLUSTER_EVENT_BUDGET);
        assertEquals(0.5f, ThoriumSoundGovernor.CLUSTER_VOLUME_FACTOR, 0.0001f);
    }

    @Test
    void aNullSoundIsIgnored() {
        assertFalse(ThoriumSoundGovernor.shouldSuppressSound(null));
    }

    @Test
    void aDisabledGovernorSuppressesNothing() {
        ThoriumSoundGovernor.setEnabled(false);
        assertFalse(ThoriumSoundGovernor.isEnabled());
        assertFalse(ThoriumSoundGovernor.shouldSuppressSound(null));
    }
}

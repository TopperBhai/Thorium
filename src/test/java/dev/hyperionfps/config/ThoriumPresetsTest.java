package dev.hyperionfps.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.core.ThoriumFeatureToggles.WeatherMode;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.texture.ThoriumTextureThrottler;

class ThoriumPresetsTest {

    @BeforeEach
    void reset() {
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
    }

    @AfterEach
    void cleanup() {
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
    }

    @Test
    void unknownPresetNamesFallBackToBalanced() {
        assertEquals(ThoriumPresets.BALANCED, ThoriumPresets.normalise("something-else"));
        assertEquals(ThoriumPresets.BALANCED, ThoriumPresets.normalise(null));
        assertEquals(ThoriumPresets.POTATO, ThoriumPresets.normalise("potato"));
        assertEquals(ThoriumPresets.NUCLEAR, ThoriumPresets.normalise("  NUCLEAR  "));
    }

    @Test
    void potatoPresetShedsLoadAndDropsTheVisuals() {
        ThoriumPresets.apply(ThoriumPresets.POTATO);

        assertTrue(AsyncOcclusionCuller.isExtremeFpsMode());
        assertEquals(4, ThoriumTextureThrottler.getThrottleDivider());
        assertEquals(WeatherMode.OFF, ThoriumFeatureToggles.getWeatherMode());
        assertEquals(30, ThoriumFeatureToggles.getAfkTimeoutSeconds());
        assertFalse(ThoriumFeatureToggles.isStutterAlertEnabled());
        assertFalse(ThoriumFeatureToggles.isReflexEnabled());
    }

    @Test
    void balancedPresetKeepsTheVisualsAndTheDiagnostics() {
        ThoriumPresets.apply(ThoriumPresets.BALANCED);

        assertFalse(AsyncOcclusionCuller.isExtremeFpsMode());
        assertEquals(2, ThoriumTextureThrottler.getThrottleDivider());
        assertEquals(WeatherMode.MINIMAL, ThoriumFeatureToggles.getWeatherMode());
        assertEquals(ThoriumFeatureToggles.DEFAULT_AFK_TIMEOUT_SECONDS,
                ThoriumFeatureToggles.getAfkTimeoutSeconds());
        assertTrue(ThoriumFeatureToggles.isStutterAlertEnabled());
        assertFalse(ThoriumFeatureToggles.isReflexEnabled());
    }

    @Test
    void nuclearPresetTurnsOnTheLowLatencyFeatures() {
        ThoriumPresets.apply(ThoriumPresets.NUCLEAR);

        assertTrue(ThoriumFeatureToggles.isReflexEnabled());
        assertTrue(ThoriumFeatureToggles.isStutterAlertEnabled());
        assertTrue(ThoriumFeatureToggles.isMobFarmEnabled());
        assertTrue(ThoriumFeatureToggles.isStorageItemFramesEnabled());
        assertTrue(ThoriumFeatureToggles.isElytraSmootherEnabled());
        assertTrue(ThoriumFeatureToggles.isExplosionShieldEnabled());
        assertTrue(ThoriumFeatureToggles.isCleanWaterFogEnabled());
        assertEquals(120, ThoriumFeatureToggles.getAfkTimeoutSeconds());
    }

    @Test
    void everyPresetLeavesTheOptInAdaptiveDistanceAlone() {
        boolean before = AdaptiveDistanceController.getInstance().isEnabled();

        ThoriumPresets.apply(ThoriumPresets.POTATO);
        assertEquals(before, AdaptiveDistanceController.getInstance().isEnabled());

        ThoriumPresets.apply(ThoriumPresets.NUCLEAR);
        assertEquals(before, AdaptiveDistanceController.getInstance().isEnabled());
    }

    @Test
    void applyingAPresetRecordsItInTheConfig() {
        ThoriumPresets.apply(ThoriumPresets.NUCLEAR);

        assertEquals(ThoriumPresets.NUCLEAR,
                ThoriumConfig.get().getString(ThoriumConfig.KEY_PRESET, ThoriumPresets.BALANCED));
    }
}

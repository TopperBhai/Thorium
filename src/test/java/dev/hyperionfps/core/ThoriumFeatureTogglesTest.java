package dev.hyperionfps.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles.Feature;
import dev.hyperionfps.core.ThoriumFeatureToggles.WeatherMode;

class ThoriumFeatureTogglesTest {

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
    void everyHeadlineFeatureIsOnByDefault() {
        for (Feature feature : Feature.values()) {
            assertTrue(feature.isEnabled(), feature + " must default to enabled");
        }
    }

    @Test
    void everyHeadlineFeatureHasItsOwnConfigKey() {
        for (Feature feature : Feature.values()) {
            assertNotNull(feature.configKey());
            assertTrue(feature.configKey().contains("."), feature + " needs a namespaced config key");
        }
    }

    @Test
    void aFeatureIsNotWiredUntilItsImplementationLands() {
        assertFalse(Feature.AFK_ECO.isWired());
        assertFalse(Feature.MOB_FARM.isWired());

        Feature.AFK_ECO.markWired();

        assertTrue(Feature.AFK_ECO.isWired());
        assertFalse(Feature.MOB_FARM.isWired(), "marking one feature must not wire another");
    }

    @Test
    void togglingOneFeatureLeavesTheOthersAlone() {
        ThoriumFeatureToggles.setMobFarmEnabled(false);

        assertFalse(ThoriumFeatureToggles.isMobFarmEnabled());
        assertTrue(ThoriumFeatureToggles.isStorageItemFramesEnabled());
        assertTrue(ThoriumFeatureToggles.isElytraSmootherEnabled());
        assertTrue(ThoriumFeatureToggles.isAfkEcoEnabled());
        assertTrue(ThoriumFeatureToggles.isStutterAlertEnabled());
        assertTrue(ThoriumFeatureToggles.isExplosionShieldEnabled());
        assertTrue(ThoriumFeatureToggles.isReflexEnabled());
        assertTrue(ThoriumFeatureToggles.isCleanWaterFogEnabled());
    }

    @Test
    void theAfkTimeoutIsClampedToASaneRange() {
        ThoriumFeatureToggles.setAfkTimeoutSeconds(0);
        assertEquals(ThoriumFeatureToggles.MIN_AFK_TIMEOUT_SECONDS,
                ThoriumFeatureToggles.getAfkTimeoutSeconds());

        ThoriumFeatureToggles.setAfkTimeoutSeconds(-900);
        assertEquals(ThoriumFeatureToggles.MIN_AFK_TIMEOUT_SECONDS,
                ThoriumFeatureToggles.getAfkTimeoutSeconds());

        ThoriumFeatureToggles.setAfkTimeoutSeconds(999999);
        assertEquals(ThoriumFeatureToggles.MAX_AFK_TIMEOUT_SECONDS,
                ThoriumFeatureToggles.getAfkTimeoutSeconds());

        ThoriumFeatureToggles.setAfkTimeoutSeconds(45);
        assertEquals(45, ThoriumFeatureToggles.getAfkTimeoutSeconds());
    }

    @Test
    void theWeatherModeParsesLeniently() {
        assertEquals(WeatherMode.VANILLA, WeatherMode.parse("VANILLA"));
        assertEquals(WeatherMode.MINIMAL, WeatherMode.parse("minimal"));
        assertEquals(WeatherMode.OFF, WeatherMode.parse(" off "));
        assertEquals(WeatherMode.MINIMAL, WeatherMode.parse("nonsense"));
        assertEquals(WeatherMode.MINIMAL, WeatherMode.parse(null));
    }

    @Test
    void theWeatherModeCyclesThroughEveryMode() {
        assertEquals(WeatherMode.MINIMAL, WeatherMode.VANILLA.next());
        assertEquals(WeatherMode.OFF, WeatherMode.MINIMAL.next());
        assertEquals(WeatherMode.VANILLA, WeatherMode.OFF.next());
    }

    @Test
    void settingANullWeatherModeIsIgnored() {
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.OFF);
        ThoriumFeatureToggles.setWeatherMode(null);

        assertEquals(WeatherMode.OFF, ThoriumFeatureToggles.getWeatherMode());
    }
}

package dev.hyperionfps.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.core.ThoriumFeatureToggles.WeatherMode;

/**
 * Tests for the No-Lag Weather and the Clean Water / Fast Fog optimiser.
 *
 * <p>{@link WeatherFogOptimizer} is a pure policy engine - it holds no Minecraft type -
 * so the whole decision table, the weather modes and both fog clamps can be exercised
 * here without a running Minecraft.</p>
 */
class WeatherFogOptimizerTest {

    @BeforeEach
    void setUp() {
        ThoriumFeatureToggles.setCleanWaterFogEnabled(true);
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.MINIMAL);
        WeatherFogOptimizer.resetForTests();
    }

    @AfterEach
    void tearDown() {
        ThoriumFeatureToggles.setCleanWaterFogEnabled(true);
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.MINIMAL);
        WeatherFogOptimizer.resetForTests();
    }

    // ------------------------------------------------------------------
    // Feature wiring
    // ------------------------------------------------------------------

    @Test
    void installMarksTheFeatureAsWired() {
        ThoriumFeatureToggles.resetWiredForTests();
        assertFalse(ThoriumFeatureToggles.Feature.CLEAN_WATER_FOG.isWired());

        WeatherFogOptimizer.install();

        assertTrue(ThoriumFeatureToggles.Feature.CLEAN_WATER_FOG.isWired());
    }

    // ------------------------------------------------------------------
    // The weather decision
    // ------------------------------------------------------------------

    @Test
    void vanillaDrawsEverything() {
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.VANILLA);

        assertTrue(WeatherFogOptimizer.shouldRenderPrecipitation());
        assertTrue(WeatherFogOptimizer.shouldRenderSplashParticles());
    }

    @Test
    void minimalKeepsTheRainButDropsTheSplashes() {
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.MINIMAL);

        assertTrue(WeatherFogOptimizer.shouldRenderPrecipitation());
        assertFalse(WeatherFogOptimizer.shouldRenderSplashParticles());
    }

    @Test
    void offRemovesThePrecipitationLayer() {
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.OFF);

        assertFalse(WeatherFogOptimizer.shouldRenderPrecipitation());
        assertFalse(WeatherFogOptimizer.shouldRenderSplashParticles());
    }

    @Test
    void theModeIsReadFromTheRegistry() {
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.OFF);
        assertEquals(WeatherMode.OFF, WeatherFogOptimizer.mode());

        ThoriumFeatureToggles.setWeatherMode(WeatherMode.VANILLA);
        assertEquals(WeatherMode.VANILLA, WeatherFogOptimizer.mode());
    }

    // ------------------------------------------------------------------
    // The clean water clamp
    // ------------------------------------------------------------------

    @Test
    void theUnderwaterFogIsClampedToAShortDistance() {
        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampUnderwaterFog(-8.0f, 96.0f);

        assertEquals(0.0f, range.start(), 0.0001f);
        assertEquals(WeatherFogOptimizer.WATER_FOG_END_MAX, range.end(), 0.0001f);
    }

    @Test
    void aShorterUnderwaterFogOnlyLosesTheStartBehindTheCamera() {
        // The water-vision potion shortens the vanilla range to 24 blocks; the end
        // stays where it is, only the start moves up to the camera.
        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampUnderwaterFog(-8.0f, 24.0f);

        assertEquals(0.0f, range.start(), 0.0001f);
        assertEquals(24.0f, range.end(), 0.0001f);
    }

    @Test
    void aBrokenUnderwaterRangeIsLeftAlone() {
        WeatherFogOptimizer.FogRange inverted =
                WeatherFogOptimizer.clampUnderwaterFog(96.0f, -8.0f);
        assertEquals(96.0f, inverted.start(), 0.0001f);
        assertEquals(-8.0f, inverted.end(), 0.0001f);

        WeatherFogOptimizer.FogRange notANumber =
                WeatherFogOptimizer.clampUnderwaterFog(Float.NaN, 96.0f);
        assertTrue(Float.isNaN(notANumber.start()));
        assertEquals(96.0f, notANumber.end(), 0.0001f);
    }

    @Test
    void theUnderwaterFogIsUntouchedWhileTheFeatureIsOff() {
        ThoriumFeatureToggles.setCleanWaterFogEnabled(false);

        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampUnderwaterFog(-8.0f, 96.0f);

        assertEquals(-8.0f, range.start(), 0.0001f);
        assertEquals(96.0f, range.end(), 0.0001f);
    }

    @Test
    void aRangeThatIsEntirelyInsideTheCapIsLeftAlone() {
        // The fog would be empty after the clamp, and an empty range reads as
        // "always fogged" in the shader.
        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampUnderwaterFog(60.0f, 96.0f);

        assertEquals(60.0f, range.start(), 0.0001f);
        assertEquals(96.0f, range.end(), 0.0001f);
    }

    // ------------------------------------------------------------------
    // The fast fog clamp
    // ------------------------------------------------------------------

    @Test
    void aWideDistanceFogBandIsNarrowed() {
        // 512 blocks of render distance: vanilla spreads the fade over 51 blocks.
        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampDistanceFog(460.8f, 512.0f);

        assertEquals(512.0f - WeatherFogOptimizer.DISTANCE_FOG_BAND_MAX, range.start(), 0.0001f);
        assertEquals(512.0f, range.end(), 0.0001f);
    }

    @Test
    void aNarrowDistanceFogBandIsLeftAlone() {
        // The default render distance: vanilla already uses a 19 block band.
        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampDistanceFog(172.8f, 192.0f);

        assertEquals(172.8f, range.start(), 0.0001f);
        assertEquals(192.0f, range.end(), 0.0001f);
    }

    @Test
    void theDistanceFogBandIsNeverWidened() {
        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampDistanceFog(100.0f, 110.0f);

        assertEquals(100.0f, range.start(), 0.0001f);
        assertEquals(110.0f, range.end(), 0.0001f);
    }

    @Test
    void aBrokenDistanceRangeIsLeftAlone() {
        WeatherFogOptimizer.FogRange inverted =
                WeatherFogOptimizer.clampDistanceFog(512.0f, 100.0f);
        assertEquals(512.0f, inverted.start(), 0.0001f);
        assertEquals(100.0f, inverted.end(), 0.0001f);

        WeatherFogOptimizer.FogRange infinite =
                WeatherFogOptimizer.clampDistanceFog(0.0f, Float.POSITIVE_INFINITY);
        assertEquals(0.0f, infinite.start(), 0.0001f);
        assertTrue(Float.isInfinite(infinite.end()));
    }

    @Test
    void theDistanceFogIsUntouchedWhileTheFeatureIsOff() {
        ThoriumFeatureToggles.setCleanWaterFogEnabled(false);

        WeatherFogOptimizer.FogRange range =
                WeatherFogOptimizer.clampDistanceFog(460.8f, 512.0f);

        assertEquals(460.8f, range.start(), 0.0001f);
        assertEquals(512.0f, range.end(), 0.0001f);
    }

    @Test
    void theBandIsCappedForTheVersionsThatComputeItThemselves() {
        assertEquals(WeatherFogOptimizer.DISTANCE_FOG_BAND_MAX,
                WeatherFogOptimizer.clampDistanceFogBand(51.2f), 0.0001f);
        assertEquals(19.2f, WeatherFogOptimizer.clampDistanceFogBand(19.2f), 0.0001f);

        ThoriumFeatureToggles.setCleanWaterFogEnabled(false);
        assertEquals(51.2f, WeatherFogOptimizer.clampDistanceFogBand(51.2f), 0.0001f);
    }

    // ------------------------------------------------------------------
    // The counters
    // ------------------------------------------------------------------

    @Test
    void theCountersRecordWhatWasDropped() {
        WeatherFogOptimizer.recordSplashSuppressed();
        WeatherFogOptimizer.recordSplashSuppressed();
        WeatherFogOptimizer.recordPrecipitationSkipped();
        WeatherFogOptimizer.recordFogClamp();

        assertEquals(2L, WeatherFogOptimizer.getSplashParticlesSuppressed());
        assertEquals(1L, WeatherFogOptimizer.getPrecipitationPassesSkipped());
        assertEquals(1L, WeatherFogOptimizer.getFogClampsApplied());
    }

    @Test
    void theCountersStartAtZero() {
        assertEquals(0L, WeatherFogOptimizer.getSplashParticlesSuppressed());
        assertEquals(0L, WeatherFogOptimizer.getPrecipitationPassesSkipped());
        assertEquals(0L, WeatherFogOptimizer.getFogClampsApplied());
    }

    @Test
    void theActivityDescriptionNamesTheMode() {
        ThoriumFeatureToggles.setWeatherMode(WeatherMode.OFF);

        String description = WeatherFogOptimizer.describeActivity();

        assertTrue(description.contains("OFF"), description);
        assertTrue(description.contains("splash particles dropped"), description);
        assertTrue(WeatherFogOptimizer.describeFogActivity().contains("fog ranges clamped"));
    }
}

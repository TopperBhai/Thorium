package dev.hyperionfps.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.core.ThoriumFeatureToggles;

class ThoriumConfigTest {

    @Test
    void parsesFlatStringObject() {
        Map<String, String> parsed = ThoriumConfig.parseFlatJson(
                "{\n  \"preset\": \"NUCLEAR\",\n  \"hud.enabled\": \"false\"\n}");

        assertEquals(2, parsed.size());
        assertEquals("NUCLEAR", parsed.get("preset"));
        assertEquals("false", parsed.get("hud.enabled"));
    }

    @Test
    void handlesEmptyAndMalformedInputWithoutThrowing() {
        assertTrue(ThoriumConfig.parseFlatJson(null).isEmpty());
        assertTrue(ThoriumConfig.parseFlatJson("").isEmpty());
        assertTrue(ThoriumConfig.parseFlatJson("   ").isEmpty());
        assertTrue(ThoriumConfig.parseFlatJson("not json at all").isEmpty());
    }

    @Test
    void keepsReadablePairsFromAnUnclosedObject() {
        Map<String, String> parsed = ThoriumConfig.parseFlatJson("{\"unterminated\": \"value\"");

        assertEquals("value", parsed.get("unterminated"));
    }

    @Test
    void recoversReadablePairsFromPartiallyMalformedInput() {
        Map<String, String> parsed = ThoriumConfig.parseFlatJson(
                "{\"preset\": \"POTATO\", garbage, \"hud.enabled\": \"true\"}");

        assertEquals("POTATO", parsed.get("preset"));
        assertEquals("true", parsed.get("hud.enabled"));
    }

    @Test
    void roundTripsThroughTheWriter() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("preset", "BALANCED");
        original.put("hud.enabled", "true");
        original.put("texture.throttler.divider", "4");

        Map<String, String> parsed = ThoriumConfig.parseFlatJson(ThoriumConfig.writeFlatJson(original));

        assertEquals(original, parsed);
    }

    @Test
    void escapesSpecialCharactersInValues() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("weird", "line1\nline2 \"quoted\" back\\slash");

        Map<String, String> parsed = ThoriumConfig.parseFlatJson(ThoriumConfig.writeFlatJson(original));

        assertEquals("line1\nline2 \"quoted\" back\\slash", parsed.get("weird"));
    }

    @Test
    void typedAccessorsFallBackWhenAbsentOrUnparsable(@TempDir Path dir) {
        ThoriumConfig config = ThoriumConfig.forFile(dir.resolve("thorium-config.json"));

        assertTrue(config.getBoolean(ThoriumConfig.KEY_HUD, true));
        assertFalse(config.getBoolean(ThoriumConfig.KEY_HUD, false));
        assertEquals(7, config.getInt(ThoriumConfig.KEY_TEXTURE_DIVIDER, 7));
        assertEquals("BALANCED", config.getString(ThoriumConfig.KEY_PRESET, "BALANCED"));

        config.setString(ThoriumConfig.KEY_TEXTURE_DIVIDER, "not-a-number");
        assertEquals(3, config.getInt(ThoriumConfig.KEY_TEXTURE_DIVIDER, 3));
    }

    @Test
    void savesAndReloadsFromDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("thorium-config.json");

        ThoriumConfig first = ThoriumConfig.forFile(file);
        first.setString(ThoriumConfig.KEY_PRESET, "NUCLEAR");
        first.setBoolean(ThoriumConfig.KEY_HUD, false);
        first.setInt(ThoriumConfig.KEY_TEXTURE_DIVIDER, 8);
        first.save();

        assertTrue(Files.isRegularFile(file), "config file should exist after save");

        ThoriumConfig second = ThoriumConfig.forFile(file);
        assertEquals("NUCLEAR", second.getString(ThoriumConfig.KEY_PRESET, "BALANCED"));
        assertFalse(second.getBoolean(ThoriumConfig.KEY_HUD, true));
        assertEquals(8, second.getInt(ThoriumConfig.KEY_TEXTURE_DIVIDER, 2));
    }

    @Test
    void saveIsAtomicAndLeavesNoTempFileBehind(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("thorium-config.json");

        ThoriumConfig config = ThoriumConfig.forFile(file);
        config.setString(ThoriumConfig.KEY_PRESET, "POTATO");
        config.save();

        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        assertFalse(Files.exists(temp), "temporary file should be moved, not left behind");
    }

    @Test
    void preservesUnknownKeysAcrossASave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("thorium-config.json");
        Files.writeString(file,
                "{\"preset\": \"BALANCED\", \"future.setting\": \"keep-me\"}",
                StandardCharsets.UTF_8);

        ThoriumConfig config = ThoriumConfig.forFile(file);
        config.setBoolean(ThoriumConfig.KEY_HUD, false);
        config.save();

        Map<String, String> reloaded = ThoriumConfig.parseFlatJson(Files.readString(file));
        assertEquals("keep-me", reloaded.get("future.setting"));
        assertEquals("false", reloaded.get(ThoriumConfig.KEY_HUD));
    }

    @Test
    void loadingAMissingFileYieldsDefaults(@TempDir Path dir) {
        ThoriumConfig config = ThoriumConfig.forFile(dir.resolve("does-not-exist.json"));

        assertTrue(config.getBoolean(ThoriumConfig.KEY_CULLING, true));
        assertEquals("BALANCED", config.getString(ThoriumConfig.KEY_PRESET, "BALANCED"));
    }

    @Test
    void applyingAndCapturingRoundTripsThroughSubsystems(@TempDir Path dir) {
        ThoriumConfig config = ThoriumConfig.forFile(dir.resolve("thorium-config.json"));

        // Must not throw even though no game is running.
        config.applyToSubsystems();
        config.captureFromSubsystems();

        assertNotNull(config.asMap());
        assertFalse(config.asMap().isEmpty(), "capturing should record subsystem states");
    }

    @Test
    void adaptiveRenderDistanceIsOptIn(@TempDir Path dir) {
        ThoriumConfig config = ThoriumConfig.forFile(dir.resolve("thorium-config.json"));
        config.applyToSubsystems();

        assertFalse(AdaptiveDistanceController.getInstance().isEnabled(),
                "the adaptive render distance rewrites a vanilla option and must be opt-in");
    }

    @Test
    void theHeadlineFeaturesDefaultToEnabled(@TempDir Path dir) {
        ThoriumConfig config = ThoriumConfig.forFile(dir.resolve("thorium-config.json"));
        config.applyToSubsystems();

        assertTrue(ThoriumFeatureToggles.isMobFarmEnabled());
        assertTrue(ThoriumFeatureToggles.isStorageItemFramesEnabled());
        assertTrue(ThoriumFeatureToggles.isElytraSmootherEnabled());
        assertTrue(ThoriumFeatureToggles.isAfkEcoEnabled());
        assertTrue(ThoriumFeatureToggles.isStutterAlertEnabled());
        assertTrue(ThoriumFeatureToggles.isExplosionShieldEnabled());
        assertTrue(ThoriumFeatureToggles.isReflexEnabled());
        assertTrue(ThoriumFeatureToggles.isCleanWaterFogEnabled());
        assertEquals(ThoriumFeatureToggles.WeatherMode.MINIMAL,
                ThoriumFeatureToggles.getWeatherMode());
        assertEquals(60, ThoriumFeatureToggles.getAfkTimeoutSeconds());
    }

    @Test
    void theHeadlineFeaturesSurviveARestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("thorium-config.json");

        ThoriumConfig first = ThoriumConfig.forFile(file);
        ThoriumFeatureToggles.setMobFarmEnabled(false);
        ThoriumFeatureToggles.setReflexEnabled(false);
        ThoriumFeatureToggles.setWeatherMode(ThoriumFeatureToggles.WeatherMode.OFF);
        ThoriumFeatureToggles.setAfkTimeoutSeconds(120);
        first.captureFromSubsystems();
        first.save();

        ThoriumConfig second = ThoriumConfig.forFile(file);
        second.applyToSubsystems();

        assertFalse(ThoriumFeatureToggles.isMobFarmEnabled());
        assertFalse(ThoriumFeatureToggles.isReflexEnabled());
        assertEquals(ThoriumFeatureToggles.WeatherMode.OFF, ThoriumFeatureToggles.getWeatherMode());
        assertEquals(120, ThoriumFeatureToggles.getAfkTimeoutSeconds());
    }

    @Test
    void everyHeadlineKeyIsPersistedUnderItsOwnName(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("thorium-config.json");

        ThoriumConfig config = ThoriumConfig.forFile(file);
        config.captureFromSubsystems();
        config.save();

        Map<String, String> written = ThoriumConfig.parseFlatJson(Files.readString(file));
        for (String key : new String[]{
                ThoriumConfig.KEY_MOB_FARM,
                ThoriumConfig.KEY_STORAGE_ITEM_FRAMES,
                ThoriumConfig.KEY_ELYTRA_SMOOTHER,
                ThoriumConfig.KEY_AFK_ECO,
                ThoriumConfig.KEY_AFK_ECO_TIMEOUT,
                ThoriumConfig.KEY_STUTTER_ALERT,
                ThoriumConfig.KEY_EXPLOSION_SHIELD,
                ThoriumConfig.KEY_REFLEX,
                ThoriumConfig.KEY_WEATHER_MODE,
                ThoriumConfig.KEY_CLEAN_WATER_FOG}) {
            assertTrue(written.containsKey(key), key + " must be written to the config file");
        }
    }

    @Test
    void theHeadlineFeatureKeysMatchTheirRegistryEntries() {
        assertEquals(ThoriumFeatureToggles.Feature.MOB_FARM.configKey(),
                ThoriumConfig.KEY_MOB_FARM);
        assertEquals(ThoriumFeatureToggles.Feature.STORAGE_ITEM_FRAMES.configKey(),
                ThoriumConfig.KEY_STORAGE_ITEM_FRAMES);
        assertEquals(ThoriumFeatureToggles.Feature.ELYTRA_SMOOTHER.configKey(),
                ThoriumConfig.KEY_ELYTRA_SMOOTHER);
        assertEquals(ThoriumFeatureToggles.Feature.AFK_ECO.configKey(),
                ThoriumConfig.KEY_AFK_ECO);
        assertEquals(ThoriumFeatureToggles.Feature.STUTTER_ALERT.configKey(),
                ThoriumConfig.KEY_STUTTER_ALERT);
        assertEquals(ThoriumFeatureToggles.Feature.EXPLOSION_SHIELD.configKey(),
                ThoriumConfig.KEY_EXPLOSION_SHIELD);
        assertEquals(ThoriumFeatureToggles.Feature.REFLEX.configKey(),
                ThoriumConfig.KEY_REFLEX);
        assertEquals(ThoriumFeatureToggles.Feature.CLEAN_WATER_FOG.configKey(),
                ThoriumConfig.KEY_CLEAN_WATER_FOG);
    }
}

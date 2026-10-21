package dev.hyperionfps.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.core.ThoriumFeatureToggles.Feature;
import dev.hyperionfps.gui.ThoriumSettingRows.Spec;

class ThoriumSettingRowsTest {

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

    private static List<Spec> allRows() {
        List<Spec> rows = new ArrayList<>();
        rows.addAll(ThoriumSettingRows.generalRows(() -> {
        }));
        rows.addAll(ThoriumSettingRows.performanceRows(true));
        rows.addAll(ThoriumSettingRows.advancedRows());
        return rows;
    }

    private static Spec rowFor(Feature feature) {
        String title = ThoriumSettingRows.titleOf(feature);
        assertNotNull(title, "no console title registered for " + feature);
        for (Spec spec : allRows()) {
            if (spec.title().equals(title)) {
                return spec;
            }
        }
        throw new AssertionError("no console row found for " + feature);
    }

    @Test
    void everyHeadlineFeatureHasExactlyOneRow() {
        List<Spec> rows = allRows();
        for (Feature feature : Feature.values()) {
            String title = ThoriumSettingRows.titleOf(feature);
            assertNotNull(title, "no console title registered for " + feature);
            long matches = rows.stream().filter(spec -> spec.title().equals(title)).count();
            assertEquals(1, matches, feature + " must have exactly one console row");
        }
    }

    @Test
    void everyRowHasATitleADescriptionAndABadge() {
        for (Spec spec : allRows()) {
            assertNotNull(spec.title());
            assertFalse(spec.title().isBlank());
            assertNotNull(spec.description());
            assertFalse(spec.description().isBlank());
            assertNotNull(spec.impactBadge());
            assertFalse(spec.impactBadge().isBlank());
        }
    }

    @Test
    void anUnwiredFeatureIsShownAsNotAvailableAndRefusesTheClick() {
        Spec row = rowFor(Feature.AFK_ECO);

        assertEquals("NOT AVAILABLE", row.status().get());
        assertFalse(row.enabled().get());

        String[] feedback = new String[1];
        row.onClick().run(message -> feedback[0] = message);

        assertEquals("NOT AVAILABLE", feedback[0]);
        assertTrue(ThoriumFeatureToggles.isAfkEcoEnabled(),
                "an unwired row must not silently change the switch");
    }

    @Test
    void aWiredFeatureTogglesAndReportsItsState() {
        Feature.AFK_ECO.markWired();
        Spec row = rowFor(Feature.AFK_ECO);

        assertEquals("ON", row.status().get());
        assertTrue(row.enabled().get());

        row.onClick().run(message -> {
        });
        assertFalse(ThoriumFeatureToggles.isAfkEcoEnabled());
        assertEquals("OFF", row.status().get());

        row.onClick().run(message -> {
        });
        assertTrue(ThoriumFeatureToggles.isAfkEcoEnabled());
    }

    @Test
    void theWeatherModeCyclesThroughTheConsoleRow() {
        Spec row = null;
        for (Spec spec : allRows()) {
            if (spec.title().equals("Weather Rendering")) {
                row = spec;
                break;
            }
        }
        assertNotNull(row, "the console must expose the weather rendering mode");

        assertEquals(ThoriumFeatureToggles.WeatherMode.MINIMAL.name(), row.status().get());
        row.onClick().run(message -> {
        });
        assertEquals(ThoriumFeatureToggles.WeatherMode.OFF, ThoriumFeatureToggles.getWeatherMode());
    }

    @Test
    void theChunkUploadPacerIsHonestAboutTheVersionItRunsOn() {
        Spec legacy = findByTitle(ThoriumSettingRows.performanceRows(true), "Chunk Upload Pacer");
        assertEquals(ThoriumSettingRows.ControlType.CYCLE, legacy.controlType());
        assertTrue(legacy.enabled().get());

        Spec modern = findByTitle(ThoriumSettingRows.performanceRows(false), "Chunk Upload Pacer");
        assertEquals("NOT AVAILABLE ON 26.x", modern.status().get());
        assertFalse(modern.enabled().get());
    }

    @Test
    void thePurgeRamRowReportsTheRealAmountRatherThanAFakeOne() {
        Spec purge = findByTitle(ThoriumSettingRows.advancedRows(), "Purge RAM Now");
        assertNotNull(purge);
        assertEquals(ThoriumSettingRows.ControlType.ACTION, purge.controlType());

        String[] feedback = new String[1];
        purge.onClick().run(message -> feedback[0] = message);

        assertNotNull(feedback[0], "the purge row must report what it reclaimed");
        assertTrue(feedback[0].endsWith("MB") || feedback[0].equals("NO CHANGE"),
                "unexpected purge feedback: " + feedback[0]);
    }

    private static Spec findByTitle(List<Spec> rows, String title) {
        for (Spec spec : rows) {
            if (spec.title().equals(title)) {
                return spec;
            }
        }
        throw new AssertionError("no row titled " + title);
    }
}

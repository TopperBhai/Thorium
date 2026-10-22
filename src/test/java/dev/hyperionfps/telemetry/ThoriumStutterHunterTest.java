package dev.hyperionfps.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.gui.ThoriumToastQueue;

class ThoriumStutterHunterTest {

    @BeforeEach
    void reset() {
        ThoriumPhaseTimer.reset();
        ThoriumGcWatcher.reset();
        ThoriumStutterHunter.reset();
        ThoriumToastQueue.clear();
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
    }

    @AfterEach
    void cleanup() {
        ThoriumPhaseTimer.reset();
        ThoriumGcWatcher.reset();
        ThoriumStutterHunter.reset();
        ThoriumToastQueue.clear();
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
    }

    private static long frameStart(long frameNanos) {
        return System.nanoTime() - frameNanos;
    }

    @Test
    void theAttributionNamesTheDominantPhase() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());
        ThoriumPhaseTimer.addEntities(16_000_000L);

        String attribution = ThoriumStutterHunter.attribute(58_000_000L, frameStart(58_000_000L));

        assertTrue(attribution.contains("Entities: 16ms"), attribution);
    }

    @Test
    void theEntityAndChunkPhasesAreSubtractedFromTheRender() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());
        ThoriumPhaseTimer.addEntities(10_000_000L);
        ThoriumPhaseTimer.addChunkUpload(6_000_000L);

        // 50 ms frame, 16 ms of it inside the entity and chunk phases.
        assertEquals(34_000_000L, ThoriumPhaseTimer.renderNanos(50_000_000L));
    }

    @Test
    void theTickIsExcludedFromTheRender() {
        long base = 1_000_000_000L;
        ThoriumPhaseTimer.beginFrame(base);
        ThoriumPhaseTimer.markWorldFrameStart(base + 12_000_000L);

        long tick = ThoriumPhaseTimer.tickNanos();
        assertEquals(12_000_000L, tick);
        assertEquals(52_000_000L, ThoriumPhaseTimer.renderNanos(52_000_000L) + tick);
    }

    @Test
    void theFramePacingWaitIsItsOwnPhase() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());
        ThoriumPhaseTimer.addSwap(9_000_000L);

        assertEquals(9_000_000L, ThoriumPhaseTimer.swapNanos());
        assertEquals(41_000_000L, ThoriumPhaseTimer.renderNanos(50_000_000L));
    }

    @Test
    void aFrameWithoutAnyMeasuredPhaseIsReportedAsUnattributed() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());

        String attribution = ThoriumStutterHunter.attribute(58_000_000L, frameStart(58_000_000L));

        assertTrue(attribution.contains("Other: 58ms"), attribution);
    }

    @Test
    void theAttributionNamesAtMostThreeContributors() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());
        ThoriumPhaseTimer.addEntities(16_000_000L);
        ThoriumPhaseTimer.addChunkUpload(12_000_000L);
        ThoriumPhaseTimer.addSwap(9_000_000L);

        String attribution = ThoriumStutterHunter.attribute(58_000_000L, frameStart(58_000_000L));

        int separators = attribution.split("\\|").length;
        assertTrue(separators <= ThoriumStutterHunter.MAX_CONTRIBUTORS + 1,
                "too many contributors: " + attribution);
    }

    @Test
    void theHunterStaysSilentWhenTheFeatureIsSwitchedOff() {
        ThoriumFeatureToggles.setStutterAlertEnabled(false);
        ThoriumPhaseTimer.beginFrame(System.nanoTime());

        ThoriumStutterHunter.onFrameEnd(58_000_000L, frameStart(58_000_000L));

        assertEquals(0L, ThoriumStutterHunter.getStutterCount());
        assertEquals("", ThoriumToastQueue.activeToast());
    }

    @Test
    void theCountersAreClearedOnReset() {
        ThoriumStutterHunter.reset();

        assertEquals(0L, ThoriumStutterHunter.getStutterCount());
        assertEquals(0L, ThoriumStutterHunter.getAttributedCount());
        assertEquals("", ThoriumStutterHunter.getLastAttribution());
    }

    @Test
    void installingTheHunterMarksTheFeatureAsWired() {
        ThoriumStutterHunter.install();

        assertTrue(ThoriumFeatureToggles.Feature.STUTTER_ALERT.isWired());
    }

    @Test
    void aGarbageCollectionInsideTheFrameIsNamed() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());
        ThoriumGcWatcher.recordPauseForTest(38_000_000L);

        String attribution = ThoriumStutterHunter.attribute(58_000_000L, frameStart(58_000_000L));

        assertTrue(attribution.contains("GC: 38ms"), attribution);
    }

    @Test
    void aGarbageCollectionOutsideTheFrameIsNotNamed() {
        ThoriumPhaseTimer.beginFrame(System.nanoTime());
        long longAgo = System.nanoTime() - 10_000_000_000L;
        ThoriumGcWatcher.recordPauseAtForTest(38_000_000L, longAgo);

        String attribution = ThoriumStutterHunter.attribute(58_000_000L, frameStart(58_000_000L));

        assertFalse(attribution.contains("GC:"), attribution);
    }

    @Test
    void aGcPauseRecordedAtTheFrameEndIsAttributedToThatFrame() throws Exception {
        long frameEnd = System.nanoTime();
        ThoriumPhaseTimer.beginFrame(frameEnd);

        GarbageCollectorMXBean[] beans = ManagementFactory.getGarbageCollectorMXBeans().toArray(new GarbageCollectorMXBean[0]);
        if (beans.length == 0) {
            return;
        }

        Field collectorsField = ThoriumGcWatcher.class.getDeclaredField("collectors");
        collectorsField.setAccessible(true);
        collectorsField.set(null, beans);

        Field namesField = ThoriumGcWatcher.class.getDeclaredField("names");
        namesField.setAccessible(true);
        namesField.set(null, java.util.Arrays.stream(beans).map(GarbageCollectorMXBean::getName).toArray(String[]::new));

        Field lastTimeField = ThoriumGcWatcher.class.getDeclaredField("LAST_COLLECTION_TIME");
        lastTimeField.setAccessible(true);
        long[] lastCollectionTime = (long[]) lastTimeField.get(null);
        if (lastCollectionTime.length > 0) {
            long base = beans[0].getCollectionTime();
            lastCollectionTime[0] = Math.max(0L, base - 38L);
        }

        ThoriumGcWatcher.poll(frameEnd);

        assertEquals(frameEnd, ThoriumGcWatcher.lastPauseAtNanos());
        assertTrue(ThoriumStutterHunter.attribute(58_000_000L, frameEnd - 58_000_000L)
                .contains("GC:"));
    }
}

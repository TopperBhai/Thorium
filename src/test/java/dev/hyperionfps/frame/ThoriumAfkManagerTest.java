package dev.hyperionfps.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumFeatureToggles;

class ThoriumAfkManagerTest {

    @BeforeEach
    void reset() {
        ThoriumAfkManager.reset();
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
    }

    @AfterEach
    void cleanup() {
        ThoriumAfkManager.reset();
        ThoriumFeatureToggles.resetToDefaults();
        ThoriumFeatureToggles.resetWiredForTests();
    }

    @Test
    void theEcoThrottleIsOffWhileThePlayerIsActive() {
        ThoriumAfkManager.update(System.nanoTime(), true);

        assertFalse(ThoriumAfkManager.isEcoActive());
        assertEquals(0L, ThoriumAfkManager.getEcoEngagements());
    }

    @Test
    void theEcoThrottleEngagesAfterTheConfiguredTimeout() {
        ThoriumFeatureToggles.setAfkTimeoutSeconds(5);
        long now = System.nanoTime();

        ThoriumAfkManager.update(now, true);
        assertFalse(ThoriumAfkManager.isEcoActive(), "the player is still active");

        ThoriumAfkManager.update(now + 6_000_000_000L, true);
        assertTrue(ThoriumAfkManager.isEcoActive(), "six seconds of silence must engage eco");
        assertEquals(1L, ThoriumAfkManager.getEcoEngagements());
    }

    @Test
    void theEcoThrottleEngagesImmediatelyWhenTheWindowLosesFocus() {
        ThoriumAfkManager.update(System.nanoTime(), false);

        assertTrue(ThoriumAfkManager.isEcoActive());
        assertFalse(ThoriumAfkManager.isWindowFocused());
    }

    @Test
    void inputActivityLiftsTheThrottleImmediately() {
        ThoriumAfkManager.update(System.nanoTime(), false);
        assertTrue(ThoriumAfkManager.isEcoActive());

        ThoriumAfkManager.onActivity();

        assertFalse(ThoriumAfkManager.isEcoActive());
        assertEquals(1L, ThoriumAfkManager.getWakeCount());
    }

    @Test
    void regainingFocusLiftsTheThrottle() {
        long now = System.nanoTime();
        ThoriumAfkManager.update(now, false);
        assertTrue(ThoriumAfkManager.isEcoActive());

        ThoriumAfkManager.update(now, true);

        assertFalse(ThoriumAfkManager.isEcoActive());
        assertTrue(ThoriumAfkManager.isWindowFocused());
    }

    @Test
    void switchingTheFeatureOffLiftsTheThrottle() {
        ThoriumAfkManager.update(System.nanoTime(), false);
        assertTrue(ThoriumAfkManager.isEcoActive());

        ThoriumFeatureToggles.setAfkEcoEnabled(false);
        ThoriumAfkManager.update(System.nanoTime(), false);

        assertFalse(ThoriumAfkManager.isEcoActive());
    }

    @Test
    void theIdleClockIsReportedInMilliseconds() throws Exception {
        long base = 1_000_000_000L;
        setLastActivityNanos(base);

        ThoriumAfkManager.update(base, true);
        long first = ThoriumAfkManager.getIdleMillis();
        assertEquals(0L, first, "a fresh activity must reset the idle clock");

        ThoriumAfkManager.update(base + 12_000_000L, true);
        long second = ThoriumAfkManager.getIdleMillis();

        assertEquals(12L, second - first,
                "twelve milliseconds of silence must show up as exactly twelve idle milliseconds");
    }

    private static void setLastActivityNanos(long nanos) throws Exception {
        Field field = ThoriumAfkManager.class.getDeclaredField("lastActivityNanos");
        field.setAccessible(true);
        field.setLong(null, nanos);
    }

    @Test
    void theBadgeIsEmptyWhileTheThrottleIsIdle() {
        ThoriumAfkManager.update(System.nanoTime(), true);

        assertEquals("", ThoriumAfkManager.getEcoBadge());
    }

    @Test
    void theBadgeNamesTheEcoFramerateWhileTheThrottleIsActive() {
        ThoriumAfkManager.update(System.nanoTime(), false);

        String badge = ThoriumAfkManager.getEcoBadge();

        assertTrue(badge.contains(String.valueOf(ThoriumAfkManager.ECO_TARGET_FPS)), badge);
    }

    @Test
    void parkingDoesNothingWhileTheThrottleIsIdle() {
        long before = ThoriumAfkManager.getParkCount();

        ThoriumAfkManager.parkEcoFrame();

        assertEquals(before, ThoriumAfkManager.getParkCount());
    }

    @Test
    void parkingWaitsOutTheEcoBudgetWhileTheThrottleIsActive() {
        ThoriumAfkManager.update(System.nanoTime(), false);
        long start = System.nanoTime();

        ThoriumAfkManager.parkEcoFrame();

        long elapsed = System.nanoTime() - start;
        assertEquals(1L, ThoriumAfkManager.getParkCount());
        assertTrue(elapsed >= ThoriumAfkManager.ECO_PARK_NANOS - 15_000_000L,
                "the park must actually wait, but it only took " + elapsed + " ns");
    }

    @Test
    void theParkIsSpentInSlicesSoAnInputEventIsNoticedQuickly() {
        assertTrue(ThoriumAfkManager.ECO_PARK_SLICE_NANOS < ThoriumAfkManager.ECO_PARK_NANOS,
                "the park has to be sliced, otherwise a click would wait for the whole park");
        assertTrue(ThoriumAfkManager.ECO_PARK_NANOS <= ThoriumAfkManager.ECO_FRAME_TIME_NANOS,
                "the park must not exceed the eco frame budget");
        assertEquals(100_000_000L, ThoriumAfkManager.ECO_FRAME_TIME_NANOS);
        assertEquals(10, ThoriumAfkManager.ECO_TARGET_FPS);
    }
}

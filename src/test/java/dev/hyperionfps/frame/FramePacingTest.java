package dev.hyperionfps.frame;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FramePacingTest {

    @Test
    void bootWindowSpansTheStartupReconfigurations() {
        assertTrue(FramePacing.inBootPhase(0L));
        assertTrue(FramePacing.inBootPhase(FramePacing.BOOT_WINDOW_MILLIS - 1));
        assertFalse(FramePacing.inBootPhase(FramePacing.BOOT_WINDOW_MILLIS));
        assertFalse(FramePacing.inBootPhase(600_000L));
    }

    @Test
    void negativeUptimeDoesNotCountAsBoot() {
        assertFalse(FramePacing.inBootPhase(-1L));
    }
}

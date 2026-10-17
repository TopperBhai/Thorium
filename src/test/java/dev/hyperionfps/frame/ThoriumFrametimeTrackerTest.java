package dev.hyperionfps.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThoriumFrametimeTrackerTest {

    @Test
    void testRecordingAndSmoothnessCalculation() throws InterruptedException {
        // Record 10 frames
        for (int i = 0; i < 10; i++) {
            Thread.sleep(2);
            ThoriumFrametimeTracker.recordFrame();
        }

        assertTrue(ThoriumFrametimeTracker.getCurrentFrametimeMs() > 0.0f);
        assertTrue(ThoriumFrametimeTracker.getSmoothnessScore() >= 0.0f);
        assertTrue(ThoriumFrametimeTracker.getSmoothnessScore() <= 100.0f);

        // Circular retrieval
        float latest = ThoriumFrametimeTracker.getSampleMs(0);
        assertEquals(ThoriumFrametimeTracker.getCurrentFrametimeMs(), latest, 0.001f);
    }
}

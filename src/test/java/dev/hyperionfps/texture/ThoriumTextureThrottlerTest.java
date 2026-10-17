package dev.hyperionfps.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThoriumTextureThrottlerTest {

    @BeforeEach
    void setUp() {
        ThoriumTextureThrottler.setEnabled(true);
        ThoriumTextureThrottler.setThrottleDivider(2);
        ThoriumTextureThrottler.resetStats();
    }

    @Test
    void testDisabledBypassesThrottling() {
        ThoriumTextureThrottler.setEnabled(false);
        assertTrue(ThoriumTextureThrottler.shouldUploadTexture("water", false));
        assertTrue(ThoriumTextureThrottler.shouldUploadTexture("lava", true));
        assertEquals(0, ThoriumTextureThrottler.getSkippedUploads());
    }

    @Test
    void testExtremeFpsModeThrottlesFluidUploads() {
        ThoriumTextureThrottler.setEnabled(true);
        // Over 4 consecutive ticks in extreme mode, only 1 should upload (3 throttled)
        int uploaded = 0;
        int skipped = 0;
        for (int i = 0; i < 4; i++) {
            if (ThoriumTextureThrottler.shouldUploadTexture("water_still", true)) {
                uploaded++;
            } else {
                skipped++;
            }
        }
        assertEquals(1, uploaded);
        assertEquals(3, skipped);
        assertEquals(3, ThoriumTextureThrottler.getSkippedUploads());
    }
}

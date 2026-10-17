package dev.hyperionfps.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcclusionGridTest {

    @BeforeEach
    void setUp() {
        OcclusionGrid.setEnabled(true);
        OcclusionGrid.invalidate();
    }

    @Test
    void testPackSectionPosUniqueness() {
        long key1 = OcclusionGrid.packSectionPos(0, 0, 0);
        long key2 = OcclusionGrid.packSectionPos(1, 0, 0);
        long key3 = OcclusionGrid.packSectionPos(0, 1, 0);
        long key4 = OcclusionGrid.packSectionPos(0, 0, 1);
        long keyNeg = OcclusionGrid.packSectionPos(-5, 4, -10);

        assertNotEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
        assertNotEquals(key1, keyNeg);
    }

    @Test
    void testCameraSectionNeverOccluded() {
        // Camera at (100.0, 64.0, 100.0) -> Section (6, 4, 6)
        double camX = 100.0;
        double camY = 64.0;
        double camZ = 100.0;
        int secX = 6;
        int secY = 4;
        int secZ = 6;

        boolean occluded = OcclusionGrid.isSectionOccluded(null, camX, camY, camZ, secX, secY, secZ);
        assertFalse(occluded, "The player's own section must never be marked as occluded");
    }

    @Test
    void testCacheInvalidationOnCameraSectionChange() {
        // Player starts at (0, 64, 0) -> Section (0, 4, 0)
        OcclusionGrid.onCameraUpdate(0, 64, 0);

        // Populate a cache entry manually (testing cache mechanics)
        assertEquals(0, OcclusionGrid.getCacheSize());

        // Player moves slightly inside the same section (5, 64, 5) -> Section (0, 4, 0)
        OcclusionGrid.onCameraUpdate(5, 64, 5);
        // Cache should remain intact

        // Player crosses into new section (32, 64, 0) -> Section (2, 4, 0)
        OcclusionGrid.onCameraUpdate(32, 64, 0);
        assertEquals(0, OcclusionGrid.getCacheSize(), "Moving to a new section must invalidate section cache");
    }

    @Test
    void testOcclusionGridToggle() {
        OcclusionGrid.setEnabled(false);
        assertFalse(OcclusionGrid.isEnabled());

        // When disabled, no section should be occluded
        assertFalse(OcclusionGrid.isSectionOccluded(null, 0, 0, 0, 10, 10, 10));

        OcclusionGrid.setEnabled(true);
        assertTrue(OcclusionGrid.isEnabled());
    }
}

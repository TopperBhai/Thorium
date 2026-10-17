package dev.hyperionfps.culling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;

class SectionCullingEngineTest {

    @BeforeEach
    void setUp() {
        SectionCullingEngine.setEnabled(true);
        SectionCullingEngine.resetMetrics();
        AsyncOcclusionCuller.onFrameBegin(0, 64, 0, null, null);
    }

    @Test
    void testNearbySectionNotCulled() {
        // Section right next to player (e.g. 16 to 32 blocks away)
        AABB box = new AABB(16, 64, 16, 32, 80, 32);
        assertTrue(SectionCullingEngine.shouldRenderSection(box));
        assertTrue(SectionCullingEngine.getSectionsRendered() > 0);
    }

    @Test
    void testDistantSectionCulled() {
        // Section very far from player (e.g. 500 blocks away)
        AABB box = new AABB(500, 64, 500, 516, 80, 516);
        assertFalse(SectionCullingEngine.shouldRenderSection(box));
        assertTrue(SectionCullingEngine.getSectionsCulled() > 0);
    }

    @Test
    void testDisabledEnginePassesAll() {
        SectionCullingEngine.setEnabled(false);
        assertFalse(SectionCullingEngine.isEnabled());

        AABB box = new AABB(1000, 64, 1000, 1016, 80, 1016);
        assertTrue(SectionCullingEngine.shouldRenderSection(box));
    }
}

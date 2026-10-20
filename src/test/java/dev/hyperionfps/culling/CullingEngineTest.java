package dev.hyperionfps.culling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CullingEngineTest {

    @Test
    void frustumCacheDefaultPassesWhenNull() {
        FrustumCache.update(null);
        assertTrue(FrustumCache.isVisible(0, 0, 0, 1, 1, 1));
    }

    @Test
    void cullerTogglesAndExtremeFpsMode() {
        AsyncOcclusionCuller.setEnabled(true);
        assertTrue(AsyncOcclusionCuller.isEnabled());

        AsyncOcclusionCuller.setExtremeFpsMode(false);
        assertFalse(AsyncOcclusionCuller.isExtremeFpsMode());

        AsyncOcclusionCuller.setExtremeFpsMode(true);
        assertTrue(AsyncOcclusionCuller.isExtremeFpsMode());

        // In extreme mode, particles beyond 20 blocks are culled
        assertTrue(AsyncOcclusionCuller.shouldCullParticle(25, 0, 0));

        AsyncOcclusionCuller.setExtremeFpsMode(false);
        assertFalse(AsyncOcclusionCuller.isExtremeFpsMode());
    }

    @Test
    void distantParticlesAreCulled() {
        AsyncOcclusionCuller.setEnabled(true);
        AsyncOcclusionCuller.setExtremeFpsMode(false);
        dev.hyperionfps.particle.ThoriumParticleBudget.resetWindow();
        dev.hyperionfps.frame.ThoriumFrameContext.beginWorldFrame(0, 0, 0, null, null);

        // Particle at distance > 32 blocks (e.g. 50 blocks away) must be culled
        assertTrue(AsyncOcclusionCuller.shouldCullParticle(50, 0, 0));
        assertTrue(AsyncOcclusionCuller.shouldCullParticle(0, 40, 0));

        // Particle close to camera (< 10 blocks) should not be culled by distance
        assertFalse(AsyncOcclusionCuller.shouldCullParticle(1, 1, 1));
    }

    @Test
    void testCacheEvictionAndClear() {
        AsyncOcclusionCuller.clearCache();
        AsyncOcclusionCuller.evictChunk(0, 0);
        // Should execute smoothly without exceptions
        assertTrue(AsyncOcclusionCuller.isEnabled());
    }
}

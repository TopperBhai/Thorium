package dev.hyperionfps.beyond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.audio.ThoriumSoundGovernor;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.input.ThoriumInputPacer;
import dev.hyperionfps.particle.ThoriumParticleSimulator;

class BeyondSodiumFeaturesTest {

    @BeforeEach
    void setUp() {
        AsyncOcclusionCuller.setEnabled(true);
        AsyncOcclusionCuller.setExtremeFpsMode(false);
        ThoriumEntityLodThrottler.setEnabled(true);
        ThoriumSoundGovernor.setEnabled(true);
        ThoriumParticleSimulator.setEnabled(true);
        ThoriumInputPacer.setEnabled(true);
    }

    @Test
    void testExtremeFpsModeToggleAndDefaults() {
        // By default, Extreme FPS mode is OFF for clean baseline
        assertFalse(AsyncOcclusionCuller.isExtremeFpsMode());

        AsyncOcclusionCuller.setExtremeFpsMode(true);
        assertTrue(AsyncOcclusionCuller.isExtremeFpsMode());

        // In extreme mode, particles beyond 12 blocks (e.g. 15 blocks away) must be culled
        AsyncOcclusionCuller.onFrameBegin(0, 0, 0, null, null);
        assertTrue(AsyncOcclusionCuller.shouldCullParticle(15, 0, 0));

        AsyncOcclusionCuller.setExtremeFpsMode(false);
        assertFalse(AsyncOcclusionCuller.isExtremeFpsMode());
    }

    @Test
    void testEntityLodThrottler() {
        assertTrue(ThoriumEntityLodThrottler.isEnabled());

        // Null entities must never throw or be throttled
        assertFalse(ThoriumEntityLodThrottler.shouldSkipAnimation(null));
        assertFalse(ThoriumEntityLodThrottler.shouldSkipSecondaryLayers(null));

        ThoriumEntityLodThrottler.setEnabled(false);
        assertFalse(ThoriumEntityLodThrottler.isEnabled());
        assertFalse(ThoriumEntityLodThrottler.shouldSkipAnimation(null));
    }

    @Test
    void testSoundGovernor() {
        assertTrue(ThoriumSoundGovernor.isEnabled());

        // Null sounds are safely ignored
        assertFalse(ThoriumSoundGovernor.shouldSuppressSound(null));

        ThoriumSoundGovernor.setEnabled(false);
        assertFalse(ThoriumSoundGovernor.isEnabled());
        assertFalse(ThoriumSoundGovernor.shouldSuppressSound(null));
    }

    @Test
    void testParticleSimulator() {
        assertTrue(ThoriumParticleSimulator.isEnabled());

        // Empty or null collections do not fail
        ThoriumParticleSimulator.tickParticles(null);
        ThoriumParticleSimulator.tickParticles(java.util.Collections.emptyList());

        ThoriumParticleSimulator.setEnabled(false);
        assertFalse(ThoriumParticleSimulator.isEnabled());
    }

    @Test
    void testInputPacer() {
        assertTrue(ThoriumInputPacer.isEnabled());
        long before = ThoriumInputPacer.getFramesPaced();
        ThoriumInputPacer.recordPacedTurn();
        assertEquals(before + 1, ThoriumInputPacer.getFramesPaced());

        ThoriumInputPacer.setEnabled(false);
        assertFalse(ThoriumInputPacer.isEnabled());
        ThoriumInputPacer.recordPacedTurn();
        assertEquals(before + 1, ThoriumInputPacer.getFramesPaced());
    }

    @Test
    void testBvhCuller() {
        assertTrue(dev.hyperionfps.culling.ThoriumBvhCuller.isEnabled());
        assertFalse(dev.hyperionfps.culling.ThoriumBvhCuller.isEntityClusterOccluded(null, 0, 0, 0, null));

        dev.hyperionfps.culling.ThoriumBvhCuller.onFrameBegin();
        dev.hyperionfps.culling.ThoriumBvhCuller.setEnabled(false);
        assertFalse(dev.hyperionfps.culling.ThoriumBvhCuller.isEnabled());
    }

    @Test
    void testFramePacer() {
        assertTrue(dev.hyperionfps.frame.ThoriumFramePacer.isEnabled());
        dev.hyperionfps.frame.ThoriumFramePacer.setTargetFps(144);
        dev.hyperionfps.frame.ThoriumFramePacer.paceFrame();

        dev.hyperionfps.frame.ThoriumFramePacer.setTargetFps(0); // Uncapped
        dev.hyperionfps.frame.ThoriumFramePacer.paceFrame();

        dev.hyperionfps.frame.ThoriumFramePacer.setEnabled(false);
        assertFalse(dev.hyperionfps.frame.ThoriumFramePacer.isEnabled());
    }
}

package dev.hyperionfps.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParticleBatchingTest {

    private ParticleBatchEngine engine;

    @BeforeEach
    void setUp() {
        engine = ParticleBatchEngine.getInstance();
        engine.setEnabled(true);
        engine.resetMetrics();
        engine.beginFrame();
    }

    @Test
    void testParticleQuadLayoutAndBillboard() {
        ParticleQuad quad = new ParticleQuad();
        quad.setBillboard(10.0f, 20.0f, 30.0f, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.5f, 0.0f, 1.0f, 240);

        float[] raw = quad.rawData();
        assertEquals(40, raw.length);

        // Check Vertex 0 (Bottom-Left: 10 - 0.5 = 9.5, 20 - 0.5 = 19.5, 30)
        assertEquals(9.5f, raw[0]);
        assertEquals(19.5f, raw[1]);
        assertEquals(30.0f, raw[2]);

        // Check Vertex 2 (Top-Right: 10 + 0.5 = 10.5, 20 + 0.5 = 20.5, 30)
        assertEquals(10.5f, raw[20]);
        assertEquals(20.5f, raw[21]);
        assertEquals(30.0f, raw[22]);
    }

    @Test
    void testParticleBatchPoolAcquisition() {
        ParticleBatchPool pool = new ParticleBatchPool(64);
        assertEquals(64, pool.getMaxQuads());
        assertEquals(0, pool.getAcquiredCount());

        for (int i = 0; i < 30; i++) {
            ParticleQuad q = pool.acquire();
            assertNotNull(q);
        }
        assertEquals(30, pool.getAcquiredCount());

        pool.reset();
        assertEquals(0, pool.getAcquiredCount());
    }

    @Test
    void testParticleBatchEngineMultiSheetSubmissionAndFlush() {
        engine.beginFrame();

        // Submit 200 fire particles to OPAQUE sheet (Sheet 1)
        for (int i = 0; i < 200; i++) {
            ParticleQuad quad = engine.acquireQuad(ParticleBatchEngine.SHEET_PARTICLE_OPAQUE);
            quad.setBillboard(i, 64, i, 0.2f, 0, 0, 1, 1, 1, 0, 0, 1, 240);
            engine.submitQuad(ParticleBatchEngine.SHEET_PARTICLE_OPAQUE, quad);
        }

        // Submit 100 smoke particles to TRANSLUCENT sheet (Sheet 2)
        for (int i = 0; i < 100; i++) {
            ParticleQuad quad = engine.acquireQuad(ParticleBatchEngine.SHEET_PARTICLE_TRANSLUCENT);
            quad.setBillboard(i, 65, i, 0.4f, 0, 0, 1, 1, 0.5f, 0.5f, 0.5f, 0.8f, 180);
            engine.submitQuad(ParticleBatchEngine.SHEET_PARTICLE_TRANSLUCENT, quad);
        }

        assertEquals(200, engine.getQueuedCount(ParticleBatchEngine.SHEET_PARTICLE_OPAQUE));
        assertEquals(100, engine.getQueuedCount(ParticleBatchEngine.SHEET_PARTICLE_TRANSLUCENT));

        AtomicInteger sheetsRendered = new AtomicInteger(0);
        AtomicInteger totalQuads = new AtomicInteger(0);

        int flushed = engine.flushAll((sheetId, count, buffer) -> {
            sheetsRendered.incrementAndGet();
            totalQuads.addAndGet(count);
            assertTrue(buffer.remaining() > 0);
        });

        assertEquals(300, flushed);
        assertEquals(300, totalQuads.get());
        assertEquals(2, sheetsRendered.get());
        assertEquals(2, engine.getSheetsFlushed());
        assertEquals(300, engine.getParticlesBatched());
        // Draw calls saved: (200 - 1) + (100 - 1) = 199 + 99 = 298
        assertEquals(298, engine.getDrawCallsSaved());
    }

    @Test
    void testDisabledParticleBatchEngine() {
        engine.setEnabled(false);
        assertFalse(engine.isEnabled());

        ParticleQuad quad = engine.acquireQuad(ParticleBatchEngine.SHEET_TERRAIN);
        engine.submitQuad(ParticleBatchEngine.SHEET_TERRAIN, quad);

        assertEquals(0, engine.getQueuedCount(ParticleBatchEngine.SHEET_TERRAIN));
        int flushed = engine.flushAll((s, c, b) -> {});
        assertEquals(0, flushed);
    }
}

package dev.hyperionfps.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionBridgeTest {

    @Test
    void legacyBridgeExposesCorrectEraAndSubBridges() {
        GameVersion version = GameVersion.parse("1.21.1");
        VersionBridge bridge = new DefaultVersionBridge(version);

        assertEquals(Era.LEGACY, bridge.era());
        assertFalse(bridge.isUnobfuscatedEra());
        assertEquals("intermediary", bridge.runtimeNamespace());

        assertNotNull(bridge.windowContext());
        assertNotNull(bridge.renderBridge());
        assertNotNull(bridge.entityBridge());
        assertNotNull(bridge.mathBridge());
    }

    @Test
    void modernBridgeExposesUnobfuscatedEra() {
        GameVersion version = GameVersion.parse("26.3");
        VersionBridge bridge = new DefaultVersionBridge(version);

        assertEquals(Era.UNOBFUSCATED, bridge.era());
        assertTrue(bridge.isUnobfuscatedEra());
        assertEquals("mojmap", bridge.runtimeNamespace());
        assertTrue(bridge.renderBridge().isModernGpuPipeline());
    }

    @Test
    void windowContextStateManagement() {
        VersionBridge bridge = new DefaultVersionBridge(GameVersion.parse("26.3"));
        VersionBridge.WindowContext ctx = bridge.windowContext();

        ctx.setWindowHandle(0x12345678L);
        assertEquals(0x12345678L, ctx.getWindowHandle());

        ctx.updateDimensions(2560, 1440);
        assertEquals(2560, ctx.getFramebufferWidth());
        assertEquals(1440, ctx.getFramebufferHeight());

        ctx.setRawFramerateLimit(240);
        assertEquals(240, ctx.getRawFramerateLimit());
    }

    @Test
    void renderAndEntityBridgeControls() {
        VersionBridge bridge = new DefaultVersionBridge(GameVersion.parse("1.21.1"));
        VersionBridge.RenderBridge render = bridge.renderBridge();
        VersionBridge.EntityBridge entity = bridge.entityBridge();

        assertTrue(render.isBatchingSupported());
        render.setRenderDistance(16);
        assertEquals(16, render.getRenderDistance());

        assertFalse(render.isExtremeFpsMode());
        render.setExtremeFpsMode(true);
        assertTrue(render.isExtremeFpsMode());

        assertTrue(entity.isCullingEnabled());
        entity.setCullingEnabled(false);
        assertFalse(entity.isCullingEnabled());

        entity.setDynamicDistanceScaling(0.75);
        assertEquals(0.75, entity.getDynamicDistanceScaling(), 0.001);
    }

    @Test
    void fastMathBridgeAccuracy() {
        VersionBridge bridge = new DefaultVersionBridge(GameVersion.parse("26.3"));
        VersionBridge.MathBridge math = bridge.mathBridge();

        // Sin accuracy within 0.001
        for (float angle = 0; angle < (float) (Math.PI * 2); angle += 0.1f) {
            float expected = (float) Math.sin(angle);
            float actual = math.sin(angle);
            assertEquals(expected, actual, 0.002f, "Sin mismatch at " + angle);
        }

        // Cos accuracy within 0.001
        for (float angle = 0; angle < (float) (Math.PI * 2); angle += 0.1f) {
            float expected = (float) Math.cos(angle);
            float actual = math.cos(angle);
            assertEquals(expected, actual, 0.002f, "Cos mismatch at " + angle);
        }

        // Fast invSqrt accuracy
        float invSqrtExpected = 1.0f / (float) Math.sqrt(16.0f);
        float invSqrtActual = math.invSqrt(16.0f);
        assertEquals(invSqrtExpected, invSqrtActual, 0.01f);

        // Fast sqrt accuracy
        float sqrtExpected = (float) Math.sqrt(25.0f);
        float sqrtActual = math.sqrt(25.0f);
        assertEquals(sqrtExpected, sqrtActual, 0.05f);
    }
}

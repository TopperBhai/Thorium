package dev.hyperionfps.instancing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityInstancingTest {

    private EntityInstancer instancer;

    @BeforeEach
    void setUp() {
        instancer = EntityInstancer.getInstance();
        instancer.setEnabled(true);
        instancer.resetMetrics();
        instancer.getGroupManager().clear();
    }

    @Test
    void testEntityInstanceDataPacking() {
        EntityInstanceData data = new EntityInstanceData();
        data.set(10.5f, 64.0f, -25.25f, 90.0f, 45.0f, 1.5f, 0x00F000F0, 0xFF0000FF);

        assertEquals(10.5f, data.x);
        assertEquals(64.0f, data.y);
        assertEquals(-25.25f, data.z);
        assertEquals(90.0f, data.yaw);
        assertEquals(45.0f, data.pitch);
        assertEquals(1.5f, data.scale);
        assertEquals(0x00F000F0, data.packedLight);
        assertEquals(0xFF0000FF, data.overlayColor);

        float[] array = new float[EntityInstanceData.FLOATS_PER_INSTANCE];
        data.writeTo(array, 0);

        assertEquals(10.5f, array[0]);
        assertEquals(64.0f, array[1]);
        assertEquals(-25.25f, array[2]);
        assertEquals(90.0f, array[3]);
        assertEquals(45.0f, array[4]);
        assertEquals(1.5f, array[5]);
        assertEquals(Float.intBitsToFloat(0x00F000F0), array[6]);
        assertEquals(Float.intBitsToFloat(0xFF0000FF), array[7]);
    }

    @Test
    void testEntityGroupGrowthAndReset() {
        EntityGroup group = new EntityGroup("minecraft:cow", 16);
        assertEquals("minecraft:cow", group.getGroupKey());
        assertEquals(0, group.getCount());
        assertEquals(16, group.getCapacity());

        // Add 25 instances (should trigger growth beyond initial capacity 16)
        for (int i = 0; i < 25; i++) {
            group.add(i, 64, i * 2, 0, 0, 1.0f, 15, 0);
        }

        assertEquals(25, group.getCount());
        assertTrue(group.getCapacity() >= 25);
        assertEquals(10.0f, group.get(10).x);
        assertEquals(20.0f, group.get(10).z);

        // Reset should keep capacity intact but reset count
        int cap = group.getCapacity();
        group.reset();
        assertEquals(0, group.getCount());
        assertEquals(cap, group.getCapacity());
    }

    @Test
    void testEntityGroupManagerAggregation() {
        EntityGroupManager manager = new EntityGroupManager();
        manager.beginFrame();

        manager.submit("minecraft:zombie", 1, 2, 3, 0, 0, 1.0f, 0, 0);
        manager.submit("minecraft:zombie", 4, 5, 6, 0, 0, 1.0f, 0, 0);
        manager.submit("minecraft:skeleton", 7, 8, 9, 0, 0, 1.0f, 0, 0);

        assertEquals(3, manager.getTotalInstanceCount());
        assertEquals(2, manager.getActiveGroupCount());
        assertEquals(2, manager.getActiveGroups().size());

        // Frame reset
        manager.beginFrame();
        assertEquals(0, manager.getTotalInstanceCount());
        assertEquals(0, manager.getActiveGroupCount());
        assertEquals(0, manager.getActiveGroups().size());
    }

    @Test
    void testEntityInstancerBatchFlushAndMetrics() {
        instancer.beginFrame();

        // Submit 100 cows and 50 pigs
        for (int i = 0; i < 100; i++) {
            instancer.submitEntity("minecraft:cow", i, 60, i, 0, 0, 1.0f, 0, 0);
        }
        for (int i = 0; i < 50; i++) {
            instancer.submitEntity("minecraft:pig", i, 60, -i, 0, 0, 1.0f, 0, 0);
        }

        AtomicInteger renderedBatches = new AtomicInteger(0);
        AtomicInteger totalInstances = new AtomicInteger(0);

        int flushed = instancer.flushAll((key, count, buffer) -> {
            renderedBatches.incrementAndGet();
            totalInstances.addAndGet(count);
            assertNotNull(buffer);
            assertTrue(count > 0);
        });

        assertEquals(150, flushed);
        assertEquals(150, totalInstances.get());
        assertEquals(2, renderedBatches.get());
        assertEquals(150, instancer.getTotalInstancesRendered());
        assertEquals(2, instancer.getBatchesDispatched());
        // Draw calls saved: (100 - 1) + (50 - 1) = 99 + 49 = 148 draw calls saved!
        assertEquals(148, instancer.getDrawCallsSaved());
    }

    @Test
    void testDisabledInstancerDoesNotCollectOrFlush() {
        instancer.setEnabled(false);
        assertFalse(instancer.isEnabled());

        instancer.beginFrame();
        instancer.submitEntity("minecraft:cow", 1, 1, 1, 0, 0, 1, 0, 0);

        int flushed = instancer.flushAll((key, count, buffer) -> {});
        assertEquals(0, flushed);
        assertEquals(0, instancer.getTotalInstancesRendered());
        assertEquals(0, instancer.getDrawCallsSaved());
    }
}

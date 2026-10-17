package dev.hyperionfps.memory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThoriumMemoryOptimizerTest {

    @Test
    void testMemoryMetrics() {
        assertTrue(ThoriumMemoryOptimizer.getMaxMemoryMb() > 0);
        assertTrue(ThoriumMemoryOptimizer.getUsedMemoryMb() >= 0);
        assertTrue(ThoriumMemoryOptimizer.getFreeMemoryMb() >= 0);
    }

    @Test
    void testPurgeMemoryDoesNotThrow() {
        long freed = ThoriumMemoryOptimizer.purgeMemoryNow();
        assertTrue(freed >= 0);
        assertTrue(ThoriumMemoryOptimizer.getLastPurgeTimestamp() > 0);
    }
}

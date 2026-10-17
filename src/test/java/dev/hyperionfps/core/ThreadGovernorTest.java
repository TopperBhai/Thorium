package dev.hyperionfps.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThreadGovernorTest {

    @Test
    void raisesNormalPriorityToMaxTarget() {
        assertEquals(Thread.MAX_PRIORITY, ThreadGovernor.targetPriority(Thread.NORM_PRIORITY));
    }

    @Test
    void neverLowersAnAlreadyMaxPriority() {
        assertEquals(Thread.MAX_PRIORITY, ThreadGovernor.targetPriority(Thread.MAX_PRIORITY));
    }

    @Test
    void tuneActuallyRaisesALiveThreadToMax() {
        Thread thread = new Thread(() -> { }, "hyperion-test-render");
        thread.setPriority(Thread.NORM_PRIORITY);

        ThreadGovernor.tuneRenderThread(thread);

        assertEquals(Thread.MAX_PRIORITY, thread.getPriority());
    }

    @Test
    void tuneWorkerRaisesPriorityToMax() {
        Thread worker = new Thread(() -> { }, "Server-Worker-1");
        worker.setPriority(Thread.NORM_PRIORITY);

        ThreadGovernor.tuneWorkerThread(worker);

        assertEquals(Thread.MAX_PRIORITY, worker.getPriority());
    }

    @Test
    void nullThreadIsIgnored() {
        assertDoesNotThrow(() -> ThreadGovernor.tuneRenderThread(null));
        assertDoesNotThrow(() -> ThreadGovernor.tuneWorkerThread(null));
    }
}

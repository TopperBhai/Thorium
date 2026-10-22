package dev.hyperionfps.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThoriumChunkUploadPacerTest {

    @BeforeEach
    void setUp() {
        ThoriumChunkUploadPacer.resetForTests();
        ElytraFlightSmoother.resetForTests();
    }

    @AfterEach
    void tearDown() {
        ThoriumChunkUploadPacer.resetForTests();
        ElytraFlightSmoother.resetForTests();
    }

    @Test
    void testBudgetedUploadsCapsPerFrame() {
        ThoriumChunkUploadPacer.setEnabled(true);
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger counter = new AtomicInteger();

        // Enqueue 20 tasks
        for (int i = 0; i < 20; i++) {
            queue.add(counter::incrementAndGet);
        }

        ThoriumChunkUploadPacer.runBudgetedUploads(queue);

        // Should have executed at most MAX_SECTIONS_PER_FRAME (8) tasks
        assertEquals(8, counter.get(), "First frame should execute at most 8 uploads");
        assertEquals(12, queue.size(), "12 tasks should remain in the queue for subsequent frames");

        // Execute next frame
        ThoriumChunkUploadPacer.runBudgetedUploads(queue);
        assertEquals(16, counter.get());
        assertEquals(4, queue.size());

        // Execute third frame
        ThoriumChunkUploadPacer.runBudgetedUploads(queue);
        assertEquals(20, counter.get());
        assertTrue(queue.isEmpty());
    }

    @Test
    void testEmptyQueue() {
        Queue<Runnable> queue = new ArrayDeque<>();
        ThoriumChunkUploadPacer.runBudgetedUploads(queue);
        ThoriumChunkUploadPacer.runBudgetedUploads(null);
        assertTrue(queue.isEmpty());
    }

    // ------------------------------------------------------------------
    // The directional queue
    // ------------------------------------------------------------------

    @Test
    void aDeferredSectionIsNotReleasedBeforeItIsDue() {
        List<String> released = new ArrayList<>();
        assertTrue(ThoriumChunkUploadPacer.deferSection(0, 64, 40, () -> released.add("ahead")));

        assertEquals(1, ThoriumChunkUploadPacer.getPendingSections());
        assertTrue(released.isEmpty(), "a deferred section must wait for the release");

        assertEquals(1, ThoriumChunkUploadPacer.releaseDeferredSections());
        assertEquals(List.of("ahead"), released);
        assertEquals(0, ThoriumChunkUploadPacer.getPendingSections());
    }

    @Test
    void theVectorBiasReleasesTheForwardConeFirst() {
        ElytraFlightSmoother.publishCamera(0.0, 64.0, 0.0);
        // Flying towards +Z at 20 m/s.
        ElytraFlightSmoother.publishVelocity(0.0, 0.0, 20.0);

        List<String> released = new ArrayList<>();
        // The three sections are deferred in the worst possible order: the one behind
        // the player is deferred first, the neutral one second and the one in front
        // of the player last.
        ThoriumChunkUploadPacer.deferSection(0, 64, -40, () -> released.add("behind"));
        ThoriumChunkUploadPacer.deferSection(40, 64, 0, () -> released.add("neutral"));
        ThoriumChunkUploadPacer.deferSection(0, 64, 40, () -> released.add("ahead"));

        assertEquals(3, ThoriumChunkUploadPacer.releaseDeferredSections());
        assertEquals(List.of("ahead", "neutral", "behind"), released,
                "the section in the forward cone must be built first");
        assertEquals(1L, ThoriumChunkUploadPacer.getReleasedForward());
    }

    @Test
    void withoutAFlightTheQueueIsDrainedInTheOrderItWasFilled() {
        List<String> released = new ArrayList<>();
        ThoriumChunkUploadPacer.deferSection(0, 64, 40, () -> released.add("first"));
        ThoriumChunkUploadPacer.deferSection(0, 64, -40, () -> released.add("second"));

        assertEquals(2, ThoriumChunkUploadPacer.releaseDeferredSections());
        assertEquals(List.of("first", "second"), released);
    }

    @Test
    void theReleaseIsBoundedPerFrame() {
        List<String> released = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int index = i;
            ThoriumChunkUploadPacer.deferSection(index, 64, 0, () -> released.add("s" + index));
        }

        assertEquals(ThoriumChunkUploadPacer.MAX_RELEASES_PER_FRAME,
                ThoriumChunkUploadPacer.releaseDeferredSections());
        assertEquals(20 - ThoriumChunkUploadPacer.MAX_RELEASES_PER_FRAME,
                ThoriumChunkUploadPacer.getPendingSections(),
                "the sections that did not fit must wait for the next frame");
    }

    @Test
    void aDisabledPacerReleasesEverythingAtOnce() {
        List<String> released = new ArrayList<>();
        ThoriumChunkUploadPacer.deferSection(0, 64, 40, () -> released.add("a"));
        ThoriumChunkUploadPacer.deferSection(0, 64, 41, () -> released.add("b"));

        ThoriumChunkUploadPacer.setEnabled(false);
        assertEquals(2, ThoriumChunkUploadPacer.releaseDeferredSections(),
                "switching the feature off must not strand the sections");
        assertEquals(2, released.size());
        assertEquals(0, ThoriumChunkUploadPacer.getPendingSections());
    }

    @Test
    void aDisabledPacerRefusesToDeferAnything() {
        ThoriumChunkUploadPacer.setEnabled(false);
        assertFalse(ThoriumChunkUploadPacer.deferSection(0, 64, 40, () -> {
        }));
        assertEquals(0, ThoriumChunkUploadPacer.getPendingSections());
    }

    @Test
    void theQueueIsBounded() {
        for (int i = 0; i < ThoriumChunkUploadPacer.MAX_DEFERRED_SECTIONS + 10; i++) {
            ThoriumChunkUploadPacer.deferSection(i, 64, 0, () -> {
            });
        }
        assertEquals(ThoriumChunkUploadPacer.MAX_DEFERRED_SECTIONS,
                ThoriumChunkUploadPacer.getPendingSections());
        assertTrue(ThoriumChunkUploadPacer.getDroppedSections() > 0,
                "the sections that did not fit must be counted");
    }

    @Test
    void aFailingSectionIsDroppedRatherThanCrashingTheFrame() {
        ThoriumChunkUploadPacer.deferSection(0, 64, 40, () -> {
            throw new IllegalStateException("the world went away");
        });

        assertEquals(0, ThoriumChunkUploadPacer.releaseDeferredSections());
        assertEquals(1L, ThoriumChunkUploadPacer.getDroppedSections());
    }

    // ------------------------------------------------------------------
    // The replay guard
    // ------------------------------------------------------------------

    @Test
    void theReplayGuardLetsTheReleaseThrough() {
        assertFalse(ThoriumChunkUploadPacer.isReplaying());

        boolean[] seen = new boolean[1];
        ThoriumChunkUploadPacer.runUndeferred(() -> seen[0] = ThoriumChunkUploadPacer.isReplaying());

        assertTrue(seen[0], "the release must be recognisable as a replay");
        assertFalse(ThoriumChunkUploadPacer.isReplaying(),
                "the guard must be cleared again afterwards");
    }

    @Test
    void theGuardIsClearedEvenWhenTheActionFails() {
        try {
            ThoriumChunkUploadPacer.runUndeferred(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // The action's own failure is the caller's business.
        }
        assertFalse(ThoriumChunkUploadPacer.isReplaying());
    }
}

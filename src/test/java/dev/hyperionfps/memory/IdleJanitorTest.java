package dev.hyperionfps.memory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class IdleJanitorTest {

    @Test
    void sweepVisitsEveryRegisteredTrimable() {
        AtomicInteger calls = new AtomicInteger();
        IdleJanitor.register(new IdleJanitor.Trimable() {
            @Override
            public long lastUsedNanos() {
                return System.nanoTime();
            }

            @Override
            public int trimIdle(long idleBudgetNanos) {
                calls.incrementAndGet();
                return 0;
            }
        });

        IdleJanitor.sweepOnce(IdleJanitor.IDLE_BUDGET_NANOS);

        assertTrue(calls.get() >= 1);
    }

    @Test
    void failingTrimableDoesNotAbortTheSweep() {
        AtomicInteger survivors = new AtomicInteger();
        IdleJanitor.register(new IdleJanitor.Trimable() {
            @Override
            public long lastUsedNanos() {
                return 0L;
            }

            @Override
            public int trimIdle(long idleBudgetNanos) {
                throw new IllegalStateException("boom");
            }
        });
        IdleJanitor.register(new IdleJanitor.Trimable() {
            @Override
            public long lastUsedNanos() {
                return 0L;
            }

            @Override
            public int trimIdle(long idleBudgetNanos) {
                survivors.incrementAndGet();
                return 0;
            }
        });

        assertDoesNotThrow(() -> IdleJanitor.sweepOnce(0L));
        assertTrue(survivors.get() >= 1);
    }
}

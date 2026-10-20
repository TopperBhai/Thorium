package dev.hyperionfps.particle;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-window particle budget.
 *
 * <p>Minecraft's {@code ParticleEngine} creates particles on demand and only enforces
 * the hard {@code ParticleLimit} caps when a particle dies. Thorium additionally caps
 * how many particles may be <em>created</em> inside one budget window, which keeps a
 * particle-heavy scene (TNT chains, mob farms, portal spammers) from flooding the
 * client with work it cannot usefully draw.</p>
 *
 * <p>This class used to be called {@code ThoriumParticleSimulator} and claimed to tick
 * particles on background threads. That was never true - the pool it created was never
 * submitted to - and the sequential re-implementation of vanilla's tick loop omitted
 * vanilla's particle-limit bookkeeping, which made the limit counters drift upwards
 * until the engine stopped creating particles altogether (state.md G-2 / G-3). Particle
 * ticking is therefore left to vanilla, and Thorium only decides which particles are
 * allowed to exist in the first place.</p>
 */
public final class ThoriumParticleBudget {

    /** Number of particles admitted per budget window in normal mode. */
    public static final int DEFAULT_BUDGET = 1500;

    /** Number of particles admitted per budget window in Extreme FPS mode. */
    public static final int EXTREME_BUDGET = 64;

    private static volatile boolean enabled = true;
    private static final AtomicInteger ADMITTED = new AtomicInteger();
    private static final AtomicLong REJECTED = new AtomicLong();

    private ThoriumParticleBudget() {
    }

    /**
     * Asks for permission to create one more particle.
     *
     * @param limit maximum number of particles admitted inside the current window
     * @return {@code true} when the particle may be created, {@code false} when the
     *         budget for this window is exhausted
     */
    public static boolean admit(int limit) {
        if (!enabled) {
            return true;
        }
        if (ADMITTED.incrementAndGet() > limit) {
            ADMITTED.decrementAndGet();
            REJECTED.incrementAndGet();
            return false;
        }
        return true;
    }

    /** Starts a new budget window. Called by the culler daemon every 50 ms. */
    public static void resetWindow() {
        ADMITTED.set(0);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            ADMITTED.set(0);
        }
    }

    /** Particles admitted inside the current window. */
    public static int getAdmitted() {
        return ADMITTED.get();
    }

    /** Particles rejected because the budget was exhausted. */
    public static long getRejected() {
        return REJECTED.get();
    }
}

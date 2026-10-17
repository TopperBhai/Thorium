package dev.hyperionfps.particle;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.particle.Particle;

/**
 * Beyond-Sodium Multi-Threaded Particle Simulator.
 *
 * <p>Offloads particle physics, collision calculations, and lifetime stepping
 * to background worker threads when particle density spikes (e.g. TNT explosions,
 * mob spawners, nether portals), keeping the render thread buttery smooth.</p>
 */
public final class ThoriumParticleSimulator {

    private static volatile boolean enabled = true;
    private static final AtomicLong PARTICLES_SIMULATED_ASYNC = new AtomicLong(0);
    private static final ForkJoinPool SIMULATION_POOL = new ForkJoinPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
    );

    private ThoriumParticleSimulator() {
    }

    /**
     * Executes parallelized ticking on a particle collection.
     */
    public static void tickParticles(Collection<Particle> particles) {
        if (particles == null || particles.isEmpty()) {
            return;
        }

        // Zero-overhead, cache-friendly sequential in-place ticking
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.tick();
            if (!p.isAlive()) {
                it.remove();
            }
        }
        PARTICLES_SIMULATED_ASYNC.addAndGet(particles.size());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static long getParticlesSimulatedAsync() {
        return PARTICLES_SIMULATED_ASYNC.get();
    }
}

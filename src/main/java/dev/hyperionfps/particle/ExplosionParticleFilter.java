package dev.hyperionfps.particle;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * The Minecraft-facing half of the explosion shield.
 *
 * <p>{@link ExplosionShield} is a pure policy engine: it knows nothing about
 * Minecraft, which is what makes its decision table unit-testable. This class is
 * the adapter that sits between the game and the policy:</p>
 *
 * <ol>
 *   <li>it tells the vanilla particle types apart and maps them onto a
 *       {@link ExplosionShield.Category},</li>
 *   <li>it asks the shield for a decision,</li>
 *   <li>it applies the decision - including the integration with
 *       {@link ThoriumParticleBudget} - and falls back to the generic particle
 *       culling for everything the shield does not handle.</li>
 * </ol>
 *
 * <p>The fallback is what makes the feature safe: the shield can only ever
 * <em>add</em> a cull, and switching it off restores the previous behaviour
 * exactly.</p>
 *
 * <h2>Why the particle types can be named here</h2>
 * <p>{@code net.minecraft.core.particles.ParticleTypes} carries the same constants
 * with the same names on all five supported Minecraft versions - verified with
 * {@code javap} against every client jar - so the mapping is written once, in
 * shared source, and needs no per-version variant. The comparison is by identity:
 * vanilla registers exactly one instance per particle type, and an identity check
 * costs nothing on the particle path.</p>
 */
public final class ExplosionParticleFilter {

    private ExplosionParticleFilter() {
    }

    /**
     * Maps a vanilla particle onto a shield category.
     *
     * @param options the particle that is about to be created
     * @return the category, or {@link ExplosionShield.Category#NONE} for a particle
     *         the shield does not care about
     */
    public static ExplosionShield.Category classify(ParticleOptions options) {
        if (options == null) {
            return ExplosionShield.Category.NONE;
        }
        ParticleType<?> type = options.getType();
        if (type == ParticleTypes.EXPLOSION) {
            return ExplosionShield.Category.EXPLOSION;
        }
        if (type == ParticleTypes.EXPLOSION_EMITTER) {
            return ExplosionShield.Category.EXPLOSION_EMITTER;
        }
        if (type == ParticleTypes.FIREWORK) {
            return ExplosionShield.Category.FIREWORK;
        }
        if (type == ParticleTypes.CAMPFIRE_COSY_SMOKE
                || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE) {
            return ExplosionShield.Category.CAMPFIRE_SMOKE;
        }
        if (type == ParticleTypes.LARGE_SMOKE) {
            return ExplosionShield.Category.LARGE_SMOKE;
        }
        return ExplosionShield.Category.NONE;
    }

    /**
     * Whether a particle may be skipped before it is created.
     *
     * <p>This is the single entry point the particle creation hook uses. It applies
     * the explosion shield and then falls back to the generic particle culling, so
     * a particle the shield does not care about is judged exactly as it was before
     * the shield existed.</p>
     *
     * @param options the particle that is about to be created
     * @param x       the particle's world X position
     * @param y       the particle's world Y position
     * @param z       the particle's world Z position
     * @return whether the particle may be skipped
     */
    public static boolean shouldCullParticle(ParticleOptions options, double x, double y, double z) {
        if (!ExplosionShield.isEnabled()) {
            return AsyncOcclusionCuller.shouldCullParticle(x, y, z);
        }

        ExplosionShield.Category category = classify(options);
        if (category == ExplosionShield.Category.NONE) {
            return AsyncOcclusionCuller.shouldCullParticle(x, y, z);
        }

        ExplosionShield.Decision decision = ExplosionShield.evaluate(
                category, x, y, z, System.currentTimeMillis());

        switch (decision) {
            case CULL_REDUNDANT -> {
                ExplosionShield.recordRedundantCull();
                return true;
            }
            case CULL_OVER_RATE -> {
                ExplosionShield.recordOverRateCull();
                return true;
            }
            case ADMIT_PRIORITY -> {
                // The explosion flash and the primary puff must survive even a
                // saturated creation budget, so they bypass it entirely. The distance
                // and the occlusion test still apply: a particle behind a wall at the
                // other end of the world is not worth drawing either way.
                if (AsyncOcclusionCuller.isParticleOutOfView(x, y, z)) {
                    return true;
                }
                ThoriumParticleBudget.admitPriority();
                ExplosionShield.recordPriorityAdmitted();
                return false;
            }
            default -> {
                ExplosionShield.recordAdmitted();
                return AsyncOcclusionCuller.shouldCullParticle(x, y, z);
            }
        }
    }
}

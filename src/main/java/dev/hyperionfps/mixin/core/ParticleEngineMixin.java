package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.particle.ExplosionParticleFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

/**
 * High-speed particle culler and limiter.
 *
 * <p>Prevents particle instantiation and simulation when the particle is outside
 * the camera frustum, occluded behind solid walls, or beyond active distance thresholds.</p>
 *
 * <p>Only the creation path is hooked. Vanilla's ticking loop is deliberately left
 * alone: it maintains the per-limit particle counters that decide whether new particles
 * may be created at all, and re-implementing it would break that bookkeeping (see
 * {@code ThoriumParticleBudget} for the full story).</p>
 *
 * <p>The decision itself belongs to {@link ExplosionParticleFilter}: it applies the
 * explosion shield and then falls back to the generic particle culling, so this
 * hook stays a one-line adapter on every version.</p>
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$cullDistantAndOccludedParticles(
            ParticleOptions options, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            CallbackInfoReturnable<Particle> cir) {
        if (ExplosionParticleFilter.shouldCullParticle(options, x, y, z)) {
            cir.setReturnValue(null);
        }
    }
}

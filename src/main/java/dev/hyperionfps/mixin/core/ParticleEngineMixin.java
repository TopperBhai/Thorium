package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

/**
 * High-speed particle culler and limiter.
 *
 * <p>Prevents particle instantiation and simulation when the particle is outside
 * the camera frustum, occluded behind solid walls, or beyond active distance thresholds.</p>
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$cullDistantAndOccludedParticles(
            ParticleOptions options, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            CallbackInfoReturnable<Particle> cir) {
        if (AsyncOcclusionCuller.shouldCullParticle(x, y, z)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tickParticleList", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$tickParticleListConcurrently(java.util.Collection<Particle> particles, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.hyperionfps.particle.ThoriumParticleSimulator.tickParticles(particles);
        ci.cancel();
    }
}

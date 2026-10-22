package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.render.WeatherFogOptimizer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * No-Lag Weather: the ground splash particles (REQ-HEAD-8).
 *
 * <p>When it rains, Minecraft spawns a splash particle on every ground block around
 * the player - roughly {@code 0.225 * (2r+1)^2 * rainLevel^2} of them <em>per tick</em>.
 * That is a few thousand particles per second, each one a real particle with its own
 * quad and its own lifetime, and none of them is distinguishable from its
 * neighbours.</p>
 *
 * <p>This hook drops exactly those particles when the player has asked for a lighter
 * weather mode. It sits on {@code ClientLevel#addParticle}, which is the entry point
 * the weather uses on <em>every</em> supported version:</p>
 *
 * <ul>
 *   <li>1.21.1 and 1.21.11 and 26.1 - {@code LevelRenderer#tickRain} and
 *       {@code WeatherEffectRenderer#tickRainParticles};</li>
 *   <li>26.2 and 26.3 - {@code ClientLevel#tickWeatherEffects}.</li>
 * </ul>
 *
 * <p>Two things are deliberately <em>not</em> touched:</p>
 * <ul>
 *   <li><b>The rain sound.</b> It is played by the very same method that spawns the
 *       particles, so the hook has to sit on the particle call and not on the method
 *       itself - otherwise the storm would go silent.</li>
 *   <li><b>The world's weather.</b> The server keeps raining, the sky keeps raining
 *       and the biome keeps raining; only the particles on the client's screen are
 *       gone.</li>
 * </ul>
 *
 * <p>The comparison is by identity: vanilla registers exactly one instance per
 * particle type, so the check costs one reference comparison and no allocation.</p>
 */
@Mixin(ClientLevel.class)
public abstract class WeatherSplashMixin {

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$suppressWeatherSplash(ParticleOptions options,
                                               double x, double y, double z,
                                               double xSpeed, double ySpeed, double zSpeed,
                                               CallbackInfo ci) {
        if (options == null || options.getType() != ParticleTypes.RAIN) {
            return;
        }
        if (!WeatherFogOptimizer.shouldRenderSplashParticles()) {
            WeatherFogOptimizer.recordSplashSuppressed();
            ci.cancel();
        }
    }
}

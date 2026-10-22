package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.render.WeatherFogOptimizer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Weather suppression on 1.21.11.
 *
 * <p>Two independent reasons may hide the precipitation layer:</p>
 * <ul>
 *   <li>Extreme FPS mode removes every cosmetic pass, the rain included.</li>
 *   <li>The No-Lag Weather mode {@code OFF} removes the precipitation layer on the
 *       client's screen. The world's weather and the rain sound stay exactly as they
 *       are - only the visual layer is gone.</li>
 * </ul>
 *
 * <p>The ground splash particles are <em>not</em> handled here: they are spawned by
 * {@code WeatherEffectRenderer#tickRainParticles}, the same method that plays the
 * rain sound, so the splash is dropped at the particle call instead - see
 * {@code dev.hyperionfps.mixin.core.WeatherSplashMixin}.</p>
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class LegacyWeatherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$suppressWeatherInExtremeMode(
            MultiBufferSource bufferSource, Vec3 cameraPosition, WeatherRenderState state,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
            return;
        }
        if (!WeatherFogOptimizer.shouldRenderPrecipitation()) {
            WeatherFogOptimizer.recordPrecipitationSkipped();
            ci.cancel();
        }
    }
}

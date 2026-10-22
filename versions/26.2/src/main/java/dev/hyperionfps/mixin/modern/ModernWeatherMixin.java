package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.render.WeatherFogOptimizer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Modern 26.2 Weather suppression.
 *
 * <p>Two independent reasons may hide the precipitation layer:</p>
 * <ul>
 *   <li>Extreme FPS mode removes every cosmetic pass, the rain included.</li>
 *   <li>The No-Lag Weather mode {@code OFF} removes the precipitation layer on the
 *       client's screen. The world's weather and the rain sound stay exactly as they
 *       are - only the visual layer is gone.</li>
 * </ul>
 *
 * <p>The ground splash particles are <em>not</em> handled here: on 26.2 they are
 * spawned by {@code ClientLevel#tickWeatherEffects}, the same method that plays the
 * rain sound, so the splash is dropped at the particle call instead - see
 * {@code dev.hyperionfps.mixin.core.WeatherSplashMixin}.</p>
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class ModernWeatherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$suppressWeatherInExtremeMode(
            Vec3 cameraPosition, WeatherRenderState state, CallbackInfo ci) {
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

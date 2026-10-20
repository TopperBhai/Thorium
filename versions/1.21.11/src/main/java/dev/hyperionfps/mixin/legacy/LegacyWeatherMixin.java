package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Weather suppression when Extreme FPS Mode is active (1.21.11).
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class LegacyWeatherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$suppressWeatherInExtremeMode(
            MultiBufferSource bufferSource, Vec3 cameraPosition, WeatherRenderState state,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
        }
    }
}

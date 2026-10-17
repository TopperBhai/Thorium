package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;

/**
 * Modern-era (26.3+) Weather suppression when Extreme FPS Mode is active.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class ModernWeatherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$suppressWeatherInExtremeMode(
            WeatherRenderState state, RenderPass pass, CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
        }
    }
}

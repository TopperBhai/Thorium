package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;

/**
 * Modern-era (26.1+) GPU UBO Fog optimization for Extreme FPS mode.
 */
@Mixin(FogRenderer.class)
public abstract class ModernFogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private void thorium$adjustModernFog(
            Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel,
            CallbackInfoReturnable<FogData> cir) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            FogData data = cir.getReturnValue();
            if (data != null) {
                data.environmentalStart = 10000.0f;
                data.renderDistanceStart = 10000.0f;
                data.environmentalEnd = 20000.0f;
                data.renderDistanceEnd = 20000.0f;
            }
        }
    }
}

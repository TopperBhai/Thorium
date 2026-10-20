package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;

/**
 * Extreme FPS mode fog suppression for 1.21.11.
 *
 * <p>1.21.11 moved the fog distances into a {@code FogData} record-like holder that the
 * fog environments fill in before the renderer uploads it to its uniform buffer. The
 * 1.21.1 hook cannot be reused because it targeted the old {@code FogRenderer#setupFog}
 * signature, so the injection sits on the atmospheric fog environment instead - that is
 * the environment that produces the terrain fog the player actually sees.</p>
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class LegacyFogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private void thorium$adjustLegacyFog(
            FogData data, Camera camera, ClientLevel level, float partialTick,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode() && data != null) {
            data.environmentalStart = 10000.0f;
            data.renderDistanceStart = 10000.0f;
            data.environmentalEnd = 20000.0f;
            data.renderDistanceEnd = 20000.0f;
        }
    }
}

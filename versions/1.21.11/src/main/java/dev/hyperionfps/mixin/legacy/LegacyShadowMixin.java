package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.entity.EntityRenderer;

/**
 * Suppresses entity shadow rendering in Extreme FPS mode to eliminate ground raycasts in 1.21.11.
 */
@Mixin(EntityRenderer.class)
public abstract class LegacyShadowMixin {

    @Inject(method = "getShadowRadius", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$suppressEntityShadows(Object state, CallbackInfoReturnable<Float> cir) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            cir.setReturnValue(0.0f);
        }
    }
}

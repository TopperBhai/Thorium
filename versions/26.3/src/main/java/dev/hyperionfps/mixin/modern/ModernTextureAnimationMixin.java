package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.texture.ThoriumTextureThrottler;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Modern 26.1 Animated texture throttler mixin.
 */
@Mixin(TextureAtlas.class)
public abstract class ModernTextureAnimationMixin {

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$throttleAnimatedTextureUploads(CallbackInfo ci) {
        if (!ThoriumTextureThrottler.shouldUploadTexture("atlas", AsyncOcclusionCuller.isExtremeFpsMode())) {
            ci.cancel();
        }
    }
}

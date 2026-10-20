package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.texture.ThoriumTextureThrottler;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Frustum and tick-aware animated texture throttling mixin.
 *
 * <p>Skips redundant {@code glTexSubImage2D} animated sprite updates when
 * throttled or in Extreme FPS mode, conserving GPU memory bus bandwidth.</p>
 */
@Mixin(TextureAtlas.class)
public abstract class LegacyTextureAnimationMixin {

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$throttleAnimatedTextureUploads(CallbackInfo ci) {
        if (!ThoriumTextureThrottler.shouldUploadTexture("atlas", AsyncOcclusionCuller.isExtremeFpsMode())) {
            ci.cancel();
        }
    }
}

package dev.hyperionfps.mixin.legacy;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * Suppresses cloud geometry rendering in Extreme FPS mode to eliminate vertex overhead.
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyCloudsMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$suppressCloudsInExtremeMode(
            PoseStack poseStack, Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
            float partialTick, double camX, double camY, double camZ,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
        }
    }
}

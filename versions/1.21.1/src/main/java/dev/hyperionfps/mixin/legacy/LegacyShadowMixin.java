package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;

/**
 * Suppresses entity shadow rendering in Extreme FPS mode to eliminate ground raycasts.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyShadowMixin {

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$suppressEntityShadows(
            PoseStack poseStack, MultiBufferSource bufferSource, Entity entity,
            float weight, float tickDelta, LevelReader level, float radius,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
        }
    }
}

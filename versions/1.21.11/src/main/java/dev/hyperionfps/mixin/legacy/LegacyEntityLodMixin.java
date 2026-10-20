package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Modern-era (1.21.11) bone animation LOD throttler for living entity models.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LegacyEntityLodMixin {

    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"
        ),
        require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void thorium$throttleLivingEntityAnimation(EntityModel<?> model, Object state) {
        if (state instanceof LivingEntityRenderState livingState) {
            int hash = System.identityHashCode(livingState);
            boolean isSpecial = livingState.nameTag != null;
            if (!ThoriumEntityLodThrottler.shouldSkipAnimation(livingState.distanceToCameraSq, hash, isSpecial)) {
                ((EntityModel) model).setupAnim((EntityRenderState) state);
            }
        } else if (state instanceof EntityRenderState renderState) {
            ((EntityModel) model).setupAnim(renderState);
        }
    }
}

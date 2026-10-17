package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

/**
 * Throttles skeletal bone animation calculations for distant living entities.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LegacyEntityLodMixin {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"
        ),
        require = 0
    )
    private void thorium$throttleLivingEntityAnimation(
            EntityModel<?> model, Entity entity,
            float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!ThoriumEntityLodThrottler.shouldSkipAnimation(entity)) {
            // @SuppressWarnings("unchecked")
            ((EntityModel<Entity>) model).setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}

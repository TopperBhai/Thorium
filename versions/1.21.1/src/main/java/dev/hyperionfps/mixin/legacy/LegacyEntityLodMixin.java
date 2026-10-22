package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.hyperionfps.anim.MobFarmOptimizer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

/**
 * Throttles skeletal bone animation calculations for distant living entities.
 *
 * <p>On 1.21.1 the renderer still works with the entity itself, so this hook can
 * both record the entity in the mob farm cluster map and ask for the decision.</p>
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
        Object species = entity.getType();
        MobFarmOptimizer.observe(species, entity.getX(), entity.getZ(),
                MobFarmOptimizer.isExemptEntity(entity));
        double distanceSq = MobFarmOptimizer.distanceSqToCamera(
                entity.getX(), entity.getY(), entity.getZ());
        if (!MobFarmOptimizer.shouldSkipAnimation(species,
                entity.getX(), entity.getY(), entity.getZ(),
                distanceSq, entity.getId(), false)) {
            @SuppressWarnings("unchecked")
            EntityModel<Entity> typed = (EntityModel<Entity>) model;
            typed.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}

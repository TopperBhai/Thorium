package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.hyperionfps.anim.MobFarmOptimizer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Modern-era (26.1+) bone animation LOD throttler for living entity models.
 *
 * <p>Since the render-state rewrite the renderer works with an extracted render
 * state instead of the entity, so the cluster map is filled by
 * {@code ModernMobFarmEntityMixin} - which still sees the entity - and this hook
 * only asks for the decision.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ModernEntityLodMixin {

    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
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
            Object species = livingState.entityType;
            if (!MobFarmOptimizer.shouldSkipAnimation(species,
                    livingState.x, livingState.y, livingState.z,
                    livingState.distanceToCameraSq, hash, isSpecial)) {
                ((EntityModel) model).setupAnim((EntityRenderState) state);
            }
        } else if (state instanceof EntityRenderState renderState) {
            ((EntityModel) model).setupAnim(renderState);
        }
    }
}

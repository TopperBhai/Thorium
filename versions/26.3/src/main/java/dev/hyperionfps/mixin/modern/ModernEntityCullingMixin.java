package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * Modern-era (26.3+) entity culling hook.
 *
 * <p>Intercepts the 6-parameter {@code shouldRender} call, delegating to
 * {@link AsyncOcclusionCuller} to prune entities outside frustum or occluded by solid blocks.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class ModernEntityCullingMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void hyperionfps$cullOccludedEntities(
            E entity, Frustum frustum, double camX, double camY, double camZ, float tickDelta,
            CallbackInfoReturnable<Boolean> cir) {
        AsyncOcclusionCuller.onFrameBegin(camX, camY, camZ, frustum, entity.level());
        if (AsyncOcclusionCuller.shouldCullEntity(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}

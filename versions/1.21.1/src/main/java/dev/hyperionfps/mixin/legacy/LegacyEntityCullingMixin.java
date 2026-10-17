package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * Classic-era entity culling hook.
 *
 * <p>Intercepts the 5-parameter {@code shouldRender} call in 1.21.1 and earlier,
 * delegating to {@link AsyncOcclusionCuller} to prune entities outside frustum
 * or behind solid walls.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyEntityCullingMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void hyperionfps$cullOccludedEntities(
            E entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (AsyncOcclusionCuller.shouldCullEntity(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}

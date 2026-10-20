package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.FrustumCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * Modern-era (1.21.11) entity culling hook.
 *
 * <p>The hook is consulted once per entity, so it must stay read-only: it publishes the
 * frame's frustum and asks the culler for a decision. The per-frame camera context is
 * published by the level-renderer hook instead.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyEntityCullingMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void hyperionfps$cullOccludedEntities(
            E entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        FrustumCache.update(frustum);
        if (AsyncOcclusionCuller.shouldCullEntity(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}

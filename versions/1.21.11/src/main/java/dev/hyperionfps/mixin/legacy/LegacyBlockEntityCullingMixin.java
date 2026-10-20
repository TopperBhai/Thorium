package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Modern-era (1.21.11) BlockEntity (Chests, Hoppers, Signs, Heads) culling hook.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class LegacyBlockEntityCullingMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void hyperionfps$cullBlockEntities(
            E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            CallbackInfoReturnable<S> cir) {
        if (AsyncOcclusionCuller.shouldCullBlockEntity(blockEntity)) {
            cir.setReturnValue(null);
        }
    }
}

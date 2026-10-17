package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Classic-era BlockEntity (Chests, Hoppers, Signs, Heads) culling hook.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class LegacyBlockEntityCullingMixin {

    @Shadow
    public Camera camera;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends BlockEntity> void hyperionfps$cullBlockEntities(
            E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            CallbackInfo ci) {
        if (this.camera != null) {
            Vec3 pos = this.camera.getPosition();
            if (AsyncOcclusionCuller.shouldCullBlockEntity(blockEntity, pos.x, pos.y, pos.z)) {
                ci.cancel();
            }
        }
    }
}

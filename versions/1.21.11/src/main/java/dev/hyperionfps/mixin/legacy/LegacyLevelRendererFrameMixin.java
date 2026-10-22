package dev.hyperionfps.mixin.legacy;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.hyperionfps.chunk.PlayerVelocityPublisher;
import dev.hyperionfps.frame.ThoriumFrameContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * Publishes Thorium's world frame at the start of {@code LevelRenderer#renderLevel}.
 *
 * <p>1.21.11 builds the culling frustum inside {@code renderLevel} and never stores it
 * in a field, so the frustum is published by the entity-culling hook instead. The camera
 * position and the level are available here.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyLevelRendererFrameMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void hyperionfps$onRenderLevelBegin(
            GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
            Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderClouds,
            CallbackInfo ci) {
        Vec3 pos = camera.position();
        // The Elytra Flight Smoother needs the flight vector of this frame before the
        // deferred chunk sections are released.
        PlayerVelocityPublisher.publish();
        ThoriumFrameContext.beginWorldFrame(pos.x, pos.y, pos.z, null, this.level);
    }
}

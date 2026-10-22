package dev.hyperionfps.mixin.modern;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.hyperionfps.chunk.ElytraFlightSmoother;
import dev.hyperionfps.chunk.PlayerVelocityPublisher;
import dev.hyperionfps.frame.ThoriumFrameContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Publishes Thorium's world frame at the start of {@code LevelRenderer#render}.
 *
 * <p>26.3 dropped the delta tracker from the world render entry point, but the camera
 * position, the culling frustum and the level are still reachable from the
 * {@code CameraRenderState} the engine passes in.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class ModernLevelRendererFrameMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void hyperionfps$onRenderLevelBegin(
            GraphicsResourceAllocator allocator, boolean renderBlockOutline,
            CameraRenderState cameraRenderState, GpuBufferSlice fogBuffer,
            Vector4f fogColor, boolean renderClouds, boolean renderSky,
            CallbackInfo ci) {
        publish(cameraRenderState);
    }

    /** Publishes the camera context of the frame that is about to be rendered. */
    private static void publish(CameraRenderState cameraRenderState) {
        if (cameraRenderState == null || cameraRenderState.pos == null) {
            ElytraFlightSmoother.clearVelocity();
            ThoriumFrameContext.beginWorldFrame(0.0, 0.0, 0.0, null, null);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // The Elytra Flight Smoother needs the flight vector of this frame before the
        // deferred chunk sections are released.
        PlayerVelocityPublisher.publish();
        ThoriumFrameContext.beginWorldFrame(
                cameraRenderState.pos.x,
                cameraRenderState.pos.y,
                cameraRenderState.pos.z,
                cameraRenderState.cullFrustum,
                client == null ? null : client.level);
    }
}

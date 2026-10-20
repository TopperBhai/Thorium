package dev.hyperionfps.mixin.legacy;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumFrameContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

/**
 * Publishes Thorium's world frame at the start of {@code LevelRenderer#renderLevel}.
 *
 * <p>1.21.1 is the only version that still keeps the culling frustum in a
 * {@code LevelRenderer} field, so the frustum can be published together with the camera
 * position.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LegacyLevelRendererFrameMixin {

    @Shadow
    private ClientLevel level;

    @Shadow
    private Frustum cullingFrustum;

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void hyperionfps$onRenderLevelBegin(
            DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
            GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
            CallbackInfo ci) {
        Vec3 pos = camera.getPosition();
        ThoriumFrameContext.beginWorldFrame(pos.x, pos.y, pos.z, this.cullingFrustum, this.level);
    }
}

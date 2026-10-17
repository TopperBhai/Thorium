package dev.hyperionfps.mixin.legacy;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.frame.ThoriumFrametimeTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

/**
 * Initializes camera and frame timing once per frame at renderLevel start.
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
        AsyncOcclusionCuller.onFrameBegin(pos.x, pos.y, pos.z, this.cullingFrustum, this.level);
        ThoriumFrametimeTracker.recordFrame();
        dev.hyperionfps.anim.ThoriumEntityLodThrottler.onFrame();
    }
}

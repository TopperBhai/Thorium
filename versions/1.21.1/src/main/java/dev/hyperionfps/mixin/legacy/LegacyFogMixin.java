package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;

/**
 * Classic-era Fog optimization without breaking sky or shader state.
 */
@Mixin(FogRenderer.class)
public abstract class LegacyFogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"), require = 0)
    private static void hyperionfps$adjustTerrainFog(
            Camera camera, FogRenderer.FogMode fogMode, float viewDistance, boolean thickFog, float partialTick,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode() && fogMode == FogRenderer.FogMode.FOG_TERRAIN) {
            RenderSystem.setShaderFogStart(viewDistance * 4.0f);
            RenderSystem.setShaderFogEnd(viewDistance * 8.0f);
        }
    }
}

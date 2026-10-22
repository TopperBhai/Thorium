package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.render.WeatherFogOptimizer;
import dev.hyperionfps.render.WeatherFogOptimizer.FogRange;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;

/**
 * Classic-era fog: Extreme FPS suppression and Clean Water / Fast Fog.
 *
 * <p>1.21.1 has a single fog range, and the game hands it to the shader through
 * {@code RenderSystem#setShaderFogStart} / {@code RenderSystem#setShaderFogEnd}. The
 * hook sits at the tail of {@code FogRenderer#setupFog}, which is where vanilla has
 * already written the values, so the clamped values overwrite them.</p>
 *
 * <p>Two things are clamped:</p>
 * <ul>
 *   <li><b>Clean water</b> - underwater, the fog end is capped, so the water reads as
 *       a short, even tint instead of a 96 block murky gradient.</li>
 *   <li><b>Fast fog</b> - above ground, the band between the fog start and the fog
 *       end is narrowed, so the horizon resolves into the fog colour over a short,
 *       sharp transition. The band is never widened, so at the default render
 *       distance nothing changes at all.</li>
 * </ul>
 *
 * <p>Lava and powdered snow keep their own fog: those are not "clean water", and
 * changing them would change the look of a dimension the player is trying to
 * survive in.</p>
 */
@Mixin(FogRenderer.class)
public abstract class LegacyFogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"), require = 0)
    private static void hyperionfps$adjustTerrainFog(
            Camera camera, FogRenderer.FogMode fogMode, float partialTick, boolean thickFog, float viewDistance,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            if (fogMode == FogRenderer.FogMode.FOG_TERRAIN) {
                RenderSystem.setShaderFogStart(viewDistance * 4.0f);
                RenderSystem.setShaderFogEnd(viewDistance * 8.0f);
            }
            return;
        }

        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.WATER) {
            float start = RenderSystem.getShaderFogStart();
            float end = RenderSystem.getShaderFogEnd();
            FogRange range = WeatherFogOptimizer.clampUnderwaterFog(start, end);
            if (range.start() == start && range.end() == end) {
                return;
            }
            RenderSystem.setShaderFogStart(range.start());
            RenderSystem.setShaderFogEnd(range.end());
            WeatherFogOptimizer.recordFogClamp();
            return;
        }
        if (fluid != FogType.NONE) {
            // Lava and powdered snow keep the fog vanilla gave them.
            return;
        }
        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN || thickFog) {
            return;
        }
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        FogRange range = WeatherFogOptimizer.clampDistanceFog(start, end);
        if (range.start() != start) {
            RenderSystem.setShaderFogStart(range.start());
            WeatherFogOptimizer.recordFogClamp();
        }
    }
}

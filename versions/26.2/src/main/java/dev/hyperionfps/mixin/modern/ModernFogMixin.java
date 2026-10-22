package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.render.WeatherFogOptimizer;
import dev.hyperionfps.render.WeatherFogOptimizer.FogRange;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;

/**
 * Modern-era (26.1+) fog: Extreme FPS suppression and Clean Water / Fast Fog.
 *
 * <p>Since 1.21.11 the fog lives in a {@link FogData} holder that the fog
 * environments fill in before the renderer uploads it to its uniform buffer. The
 * holder carries <em>two</em> ranges:</p>
 * <ul>
 *   <li>{@code environmentalStart} / {@code environmentalEnd} - the fog of the
 *       environment the camera is in. Underwater that is the water fog.</li>
 *   <li>{@code renderDistanceStart} / {@code renderDistanceEnd} - the fog that hides
 *       the world at the edge of the render distance.</li>
 * </ul>
 *
 * <p>Clean Water clamps the first range while the camera is in water; Fast Fog
 * narrows the band of the second one. The band is never widened, so at the default
 * render distance - where vanilla already uses a 19 block band - nothing changes at
 * all.</p>
 */
@Mixin(FogRenderer.class)
public abstract class ModernFogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private void thorium$adjustModernFog(
            Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel,
            CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data == null) {
            return;
        }
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            data.environmentalStart = 10000.0f;
            data.renderDistanceStart = 10000.0f;
            data.environmentalEnd = 20000.0f;
            data.renderDistanceEnd = 20000.0f;
            return;
        }
        if (!ThoriumFeatureToggles.isCleanWaterFogEnabled()) {
            return;
        }

        if (camera.getFluidInCamera() == FogType.WATER) {
            FogRange range = WeatherFogOptimizer.clampUnderwaterFog(
                    data.environmentalStart, data.environmentalEnd);
            if (range.start() == data.environmentalStart && range.end() == data.environmentalEnd) {
                return;
            }
            data.environmentalStart = range.start();
            data.environmentalEnd = range.end();
            WeatherFogOptimizer.recordFogClamp();
            return;
        }

        FogRange range = WeatherFogOptimizer.clampDistanceFog(
                data.renderDistanceStart, data.renderDistanceEnd);
        if (range.start() == data.renderDistanceStart && range.end() == data.renderDistanceEnd) {
            return;
        }
        data.renderDistanceStart = range.start();
        data.renderDistanceEnd = range.end();
        WeatherFogOptimizer.recordFogClamp();
    }
}

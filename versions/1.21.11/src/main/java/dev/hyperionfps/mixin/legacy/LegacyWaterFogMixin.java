package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.render.WeatherFogOptimizer;
import dev.hyperionfps.render.WeatherFogOptimizer.FogRange;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;

/**
 * Clean Water on 1.21.11: the underwater fog is clamped to a short distance.
 *
 * <p>Since 1.21.11 the fog lives in a {@link FogData} holder that the fog
 * environments fill in before the renderer uploads it to its uniform buffer. The
 * water environment owns the underwater fog, so the clamp sits here: the hook runs
 * after the environment has written its values and before the renderer uploads
 * them, so the clamped range is the one the shader sees.</p>
 *
 * <p>The <em>distance</em> fog is a different range in the same holder - see
 * {@code LegacyFogRendererMixin} - and is clamped separately.</p>
 */
@Mixin(WaterFogEnvironment.class)
public abstract class LegacyWaterFogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private void thorium$clampUnderwaterFog(FogData data, Camera camera, ClientLevel level,
                                            float partialTick, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (data == null) {
            return;
        }
        FogRange range = WeatherFogOptimizer.clampUnderwaterFog(
                data.environmentalStart, data.environmentalEnd);
        if (range.start() == data.environmentalStart && range.end() == data.environmentalEnd) {
            return;
        }
        data.environmentalStart = range.start();
        data.environmentalEnd = range.end();
        WeatherFogOptimizer.recordFogClamp();
    }
}

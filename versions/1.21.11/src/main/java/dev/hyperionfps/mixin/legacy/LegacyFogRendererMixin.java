package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.render.WeatherFogOptimizer;
import net.minecraft.client.renderer.fog.FogRenderer;

/**
 * Fast Fog on 1.21.11: the distance fog band is narrowed.
 *
 * <p>Since 1.21.11 the renderer derives the distance fog from the render distance:
 * {@code renderDistanceStart = viewDistance - clamp(viewDistance/10, 4, 64)} and
 * {@code renderDistanceEnd = viewDistance}. The band between the two is what makes
 * the horizon fade in gradually.</p>
 *
 * <p>The hook sits on the {@code Mth#clamp} call that produces the band, so the
 * narrowed band is the one the renderer applies. Narrowing never widens the band,
 * which means that at the default render distance - where vanilla already uses a
 * 19 block band - nothing changes at all.</p>
 */
@Mixin(FogRenderer.class)
public abstract class LegacyFogRendererMixin {

    @Redirect(method = "setupFog",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"),
              require = 0)
    private float thorium$fastFogBand(float value, float min, float max) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            // Extreme FPS mode owns the fog on this node; leave its band alone.
            return Math.min(Math.max(value, min), max);
        }
        float band = Math.min(Math.max(value, min), max);
        return WeatherFogOptimizer.clampDistanceFogBand(band);
    }
}

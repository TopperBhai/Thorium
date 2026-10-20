package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.core.ThoriumCompatibility;
import dev.hyperionfps.version.VersionBridge;
import dev.hyperionfps.version.VersionBridgeRegistry;
import net.minecraft.util.Mth;

/**
 * Legacy-era (pre-26.1) FastMath lookup table replacement for Minecraft's math utility.
 * Targets sin(float) and cos(float).
 *
 * <p>Only the {@code float}-returning overloads are overridden. The double-returning
 * {@code atan2(double, double)} and {@code invSqrt(double)} are deliberately left
 * alone: truncating them to {@code float} would lose precision that the vanilla
 * implementation keeps, and these helpers feed the client's movement prediction.</p>
 *
 * <p>The overrides are disabled outright when a shader pack is loaded, because shader
 * packs depend on the exact precision of vanilla's math.</p>
 */
@Mixin(Mth.class)
public abstract class LegacyMthMixin {

    @Inject(method = "sin(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastSin(float f, CallbackInfoReturnable<Float> cir) {
        if (!ThoriumCompatibility.isFastMathEnabled()) {
            return;
        }
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.sin(f));
    }

    @Inject(method = "cos(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastCos(float f, CallbackInfoReturnable<Float> cir) {
        if (!ThoriumCompatibility.isFastMathEnabled()) {
            return;
        }
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.cos(f));
    }

    @Inject(method = "invSqrt(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastInvSqrtFloat(float f, CallbackInfoReturnable<Float> cir) {
        if (!ThoriumCompatibility.isFastMathEnabled()) {
            return;
        }
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.invSqrt(f));
    }
}

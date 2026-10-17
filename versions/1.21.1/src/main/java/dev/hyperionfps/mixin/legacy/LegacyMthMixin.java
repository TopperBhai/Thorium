package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.version.VersionBridge;
import dev.hyperionfps.version.VersionBridgeRegistry;
import net.minecraft.util.Mth;

/**
 * Legacy-era (pre-26.1) FastMath lookup table replacement for Minecraft's math utility.
 * Targets sin(float) and cos(float).
 */
@Mixin(Mth.class)
public abstract class LegacyMthMixin {

    @Inject(method = "sin(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastSin(float f, CallbackInfoReturnable<Float> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.sin(f));
    }

    @Inject(method = "cos(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastCos(float f, CallbackInfoReturnable<Float> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.cos(f));
    }

    @Inject(method = "atan2(DD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastAtan2(double y, double x, CallbackInfoReturnable<Double> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue((double) math.atan2((float) y, (float) x));
    }

    @Inject(method = "invSqrt(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastInvSqrtFloat(float f, CallbackInfoReturnable<Float> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.invSqrt(f));
    }

    @Inject(method = "invSqrt(D)D", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastInvSqrtDouble(double d, CallbackInfoReturnable<Double> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue((double) math.invSqrt((float) d));
    }
}

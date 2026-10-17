package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.version.VersionBridge;
import dev.hyperionfps.version.VersionBridgeRegistry;
import net.minecraft.util.Mth;

/**
 * Modern-era (26.1+) FastMath lookup table replacement for Minecraft's math utility.
 * Targets sin(double) and cos(double) which Mojang upgraded in the unobfuscated era.
 */
@Mixin(Mth.class)
public abstract class ModernMthMixin {

    @Inject(method = "sin(D)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastSin(double d, CallbackInfoReturnable<Float> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.sin((float) d));
    }

    @Inject(method = "cos(D)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hyperionfps$fastCos(double d, CallbackInfoReturnable<Float> cir) {
        VersionBridge.MathBridge math = VersionBridgeRegistry.get().mathBridge();
        cir.setReturnValue(math.cos((float) d));
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

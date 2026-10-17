package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

/**
 * Bypasses focus-loss FPS throttling and strips hidden framerate caps.
 *
 * <p>Third-party launchers and overlay hooks frequently cause Minecraft to
 * detect loss of window focus, triggering vanilla's built-in 30 FPS cap and
 * background frame throttle. This mixin forces the active state to remain true
 * unless explicitly configured otherwise via {@code -Dhyperionfps.throttle_on_focus_loss=true}.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFramerateUncapperMixin {

    private static final boolean THROTTLE_ON_LOST_FOCUS = Boolean.getBoolean("hyperionfps.throttle_on_focus_loss");

    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$bypassFocusLossThrottling(CallbackInfoReturnable<Boolean> cir) {
        if (!THROTTLE_ON_LOST_FOCUS) {
            cir.setReturnValue(true);
        }
    }
}

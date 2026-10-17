package dev.hyperionfps.mixin.modern;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;
import dev.hyperionfps.frame.FramePacing;

/**
 * Modern 26.1 GLFW frame uncapper and VSync enforcer.
 */
@Mixin(Window.class)
public abstract class ModernFramePacingMixin {

    @Inject(method = "updateVsync", at = @At("TAIL"), require = 0)
    private void hyperionfps$enforceUncappedVsync(boolean vsyncEnabled, CallbackInfo callbackInfo) {
        if (!vsyncEnabled || FramePacing.inBootPhase()) {
            GLFW.glfwSwapInterval(0);
            FramePacing.reportUncapped("GLFW glfwSwapInterval(0)");
        }
    }
}

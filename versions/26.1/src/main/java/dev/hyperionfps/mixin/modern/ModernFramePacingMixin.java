package dev.hyperionfps.mixin.modern;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;
import dev.hyperionfps.core.ThoriumSafety;
import dev.hyperionfps.frame.FramePacing;

/**
 * Modern 26.1 GLFW frame uncapper and VSync enforcer.
 */
@Mixin(Window.class)
public abstract class ModernFramePacingMixin {

    @Inject(method = "updateVsync", at = @At("TAIL"), require = 0)
    private void hyperionfps$enforceUncappedVsync(boolean vsyncEnabled, CallbackInfo callbackInfo) {
        if (ThoriumSafety.isSafeMode()) {
            return;
        }
        if (!vsyncEnabled || FramePacing.inBootPhase()) {
            if (applySwapIntervalZero()) {
                FramePacing.reportUncapped("GLFW glfwSwapInterval(0)");
            } else {
                FramePacing.reportUncappedRejected("GLFW glfwSwapInterval(0)");
            }
        }
    }

    /**
     * Applies a zero swap interval, but only when a GL context is actually current.
     *
     * <p>{@code glfwSwapInterval} is undefined behaviour without a current context and
     * window. On software rasterisers and on Android OpenGL translation layers
     * (Pojav/RxLauncher with LTW) that has been observed to abort the process with a
     * native SIGSEGV rather than raise a Java error, so the guard is mandatory rather
     * than defensive.</p>
     */
    private static boolean applySwapIntervalZero() {
        try {
            // LWJGL exposes glfwGetCurrentContext() but not glfwGetCurrentWindow();
            // a null context is sufficient to prove there is nothing to configure.
            if (GLFW.glfwGetCurrentContext() == 0L) {
                return false;
            }
            GLFW.glfwSwapInterval(0);
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }
}

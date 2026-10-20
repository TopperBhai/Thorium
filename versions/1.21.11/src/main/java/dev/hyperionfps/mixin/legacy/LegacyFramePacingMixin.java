package dev.hyperionfps.mixin.legacy;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

import dev.hyperionfps.frame.FramePacing;

/**
 * Classic-line (GLFW) frame uncapper and VSync enforcer.
 *
 * <p>Forces true {@code glfwSwapInterval(0)} when VSync is disabled or during
 * the boot phase, stripping any driver or cracked launcher VSync lockups.</p>
 */
@Mixin(Window.class)
public abstract class LegacyFramePacingMixin {

    @Inject(method = "updateVsync", at = @At("TAIL"), require = 0)
    private static void hyperionfps$enforceUncappedVsync(boolean vsyncEnabled, CallbackInfo callbackInfo) {
        if (!vsyncEnabled || FramePacing.inBootPhase()) {
            GLFW.glfwSwapInterval(0);
            FramePacing.reportUncapped("GLFW glfwSwapInterval(0)");
        }
    }
}

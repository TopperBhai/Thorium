package dev.hyperionfps.mixin.modern;

import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.backend.opengl.GlSurface;

import dev.hyperionfps.frame.FramePacing;

/**
 * Modern-era (SDL3) frame uncapper and VSync enforcer.
 *
 * <p>Forces true swap interval 0 when VSync is disabled (IMMEDIATE / MAILBOX present mode)
 * or during the boot phase, eliminating launcher or driver present-mode lockups.</p>
 */
@Mixin(GlSurface.class)
public abstract class ModernFramePacingMixin {

    @Inject(method = "configure", at = @At("TAIL"), require = 0)
    private static void hyperionfps$enforceUncappedVsync(GpuSurface.Configuration configuration, CallbackInfo callbackInfo) {
        boolean vsyncDisabled = configuration.presentMode() != GpuSurface.PresentMode.FIFO
                && configuration.presentMode() != GpuSurface.PresentMode.FIFO_RELAXED;
        if (FramePacing.inBootPhase() || vsyncDisabled) {
            if (SDLVideo.SDL_GL_SetSwapInterval(0)) {
                FramePacing.reportUncapped("SDL3 SDL_GL_SetSwapInterval(0)");
            } else {
                FramePacing.reportUncappedRejected("SDL3 SDL_GL_SetSwapInterval(0)");
            }
        }
    }
}

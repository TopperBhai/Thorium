package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumFrameContext;
import net.minecraft.client.Minecraft;

/**
 * Opens Thorium's client frame.
 *
 * <p>{@code Minecraft#runTick(boolean)} is the body of Minecraft's main loop: it is
 * invoked exactly once per iteration of the loop, on every supported Minecraft version
 * (the descriptor {@code (Z)V} is identical on 1.21.1, 1.21.11, 26.1, 26.2 and 26.3).
 * It is therefore the one hook that is guaranteed to fire exactly once per frame, in a
 * world as well as in the menus.</p>
 *
 * <p>The frametime tracker and the frame pacer are driven from here so that they cannot
 * be called twice per frame - which is what the HUD draw hook used to do on the
 * versions where the HUD was extracted from the render state.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameBoundaryMixin {

    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void hyperionfps$openClientFrame(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        // The menus are never throttled: the pacer only runs while a world is loaded.
        // The focus state drives the AFK power saver, which throttles both.
        ThoriumFrameContext.beginClientFrame(client.level != null, client.isWindowActive());
    }
}

package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumAfkManager;
import net.minecraft.client.MouseHandler;

/**
 * Reports mouse movement to the AFK manager.
 *
 * <p>{@code MouseHandler#onMove} is the GLFW cursor-position callback. It exists on
 * every supported Minecraft version - the descriptor changed in 26.3, which added the
 * movement deltas, but the method name did not - so the selector is given without a
 * descriptor and the handler takes no arguments at all. That keeps a single core
 * mixin valid on all five version nodes.</p>
 *
 * <p>The handler only stamps a timestamp, so it costs a single
 * {@link System#nanoTime()} call per mouse event.</p>
 */
@Mixin(MouseHandler.class)
public abstract class AfkMouseActivityMixin {

    @Inject(method = "onMove", at = @At("HEAD"), require = 0)
    private void hyperionfps$onMouseMove(CallbackInfo ci) {
        ThoriumAfkManager.onActivity();
    }
}

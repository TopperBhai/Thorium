package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.core.RenderThreads;
import net.minecraft.client.Minecraft;

/**
 * Captures the render thread while the client instance is being constructed -
 * the earliest moment the owning thread is observable from a cross-version
 * hook. {@code net.minecraft.client.Minecraft} exists under this name in every
 * supported version (it is Mojang's own class name, stable across the
 * obfuscation boundary).
 *
 * <p>Two constraints shape this hook. It sits at {@code HEAD} of the
 * constructor (not {@code TAIL}): Fabric dispatches the mod's
 * {@code onInitializeClient} from inside that same constructor, so a
 * {@code TAIL} capture only happens after the status line meant to verify it
 * has already been printed. And the handler is {@code static} because code
 * placed before the {@code super()} invocation cannot legally touch
 * {@code this} - Mixin rejects (and with {@code require = 0} logs rather than
 * throws) a non-static handler there. The capture itself only needs
 * {@code Thread.currentThread()}, so no instance state is required.</p>
 *
 * <p>The injection is non-required on purpose: if a future version ever
 * restructures construction, capture degrades to "not captured" with a log
 * line instead of a crash.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftThreadCaptureMixin {

    @Inject(method = "<init>", at = @At("HEAD"), require = 0)
    private static void hyperionfps$captureRenderThread(CallbackInfo callbackInfo) {
        RenderThreads.captureClientThread();
    }
}

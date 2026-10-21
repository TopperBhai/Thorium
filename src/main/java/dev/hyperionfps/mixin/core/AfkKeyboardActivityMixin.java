package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumAfkManager;
import net.minecraft.client.KeyboardHandler;

/**
 * Reports key presses to the AFK manager.
 *
 * <p>{@code KeyboardHandler#keyPress} exists on every supported Minecraft version,
 * but its descriptor changed with the move to the {@code InputConstants} key-event
 * API in 1.21.11. The selector is therefore given without a descriptor and the
 * handler takes no arguments, which keeps one core mixin valid on all five nodes.</p>
 */
@Mixin(KeyboardHandler.class)
public abstract class AfkKeyboardActivityMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void hyperionfps$onKeyPress(CallbackInfo ci) {
        ThoriumAfkManager.onActivity();
    }
}

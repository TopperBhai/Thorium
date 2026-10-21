package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumAfkManager;
import net.minecraft.client.MouseHandler;

/**
 * Reports mouse clicks to the AFK manager (26.1+).
 *
 * <p>The 26.x nodes deliver the mouse button through {@code MouseHandler#onButton},
 * the same name 1.21.11 uses. 1.21.1 still has {@code onPress}, so the legacy nodes
 * carry their own hook and the build-time mixin audit proves that each one targets a
 * method that really exists on its node.</p>
 */
@Mixin(MouseHandler.class)
public abstract class ModernAfkClickMixin {

    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void hyperionfps$onMouseButton(CallbackInfo ci) {
        ThoriumAfkManager.onActivity();
    }
}
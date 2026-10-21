package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumAfkManager;
import net.minecraft.client.MouseHandler;

/**
 * Reports mouse clicks to the AFK manager (1.21.1).
 *
 * <p>1.21.1 delivers the mouse button through {@code MouseHandler#onPress}. 1.21.11
 * and the 26.x nodes use {@code onButton} instead, so the two eras need their own
 * hook. The build-time mixin audit proves that each one targets a method that really
 * exists on its node - a stale target fails the build instead of silently doing
 * nothing.</p>
 */
@Mixin(MouseHandler.class)
public abstract class LegacyAfkClickMixin {

    @Inject(method = "onPress", at = @At("HEAD"), require = 0)
    private void hyperionfps$onMousePress(CallbackInfo ci) {
        ThoriumAfkManager.onActivity();
    }
}

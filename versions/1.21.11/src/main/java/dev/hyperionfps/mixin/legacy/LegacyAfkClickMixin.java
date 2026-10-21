package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.frame.ThoriumAfkManager;
import net.minecraft.client.MouseHandler;

/**
 * Reports mouse clicks to the AFK manager (1.21.11).
 *
 * <p>1.21.11 moved the mouse button callback from {@code onPress} to {@code onButton},
 * which is also the name the 26.x nodes use. 1.21.1 still has {@code onPress}, so
 * the two legacy nodes carry their own hook and the build-time mixin audit proves
 * that each one targets a method that really exists on its node.</p>
 */
@Mixin(MouseHandler.class)
public abstract class LegacyAfkClickMixin {

    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void hyperionfps$onMouseButton(CallbackInfo ci) {
        ThoriumAfkManager.onActivity();
    }
}

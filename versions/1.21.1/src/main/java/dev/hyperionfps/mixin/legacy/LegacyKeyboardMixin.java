package dev.hyperionfps.mixin.legacy;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.gui.ThoriumGuiManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Classic-era keyboard hook for Shift + F10 Extreme FPS Mode toggle.
 */
@Mixin(KeyboardHandler.class)
public abstract class LegacyKeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void hyperionfps$onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_F10 && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            Component notification = ThoriumGuiManager.toggleExtremeFpsMode();
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.displayClientMessage(notification, true);
            }
        }
    }
}

package dev.hyperionfps.mixin.legacy;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.gui.ThoriumGuiManager;
import dev.hyperionfps.gui.ThoriumVideoSettingsScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Classic-era keyboard shortcuts for Thorium in 1.21.1.
 *
 * <p>1.21.1 still delivers key events through the GLFW callback signature
 * {@code (window, key, scancode, action, modifiers)}, unlike 1.21.11+ which moved to
 * the {@code InputConstants} key-event API. The bindings are identical to the newer
 * nodes:</p>
 * <ul>
 *   <li>F8: Open Thorium Settings Console</li>
 *   <li>F6: Toggle Nuclear Performance HUD</li>
 *   <li>F7 / Shift+F10: Toggle Extreme FPS Mode</li>
 * </ul>
 */
@Mixin(KeyboardHandler.class)
public abstract class LegacyKeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void hyperionfps$onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (key == GLFW.GLFW_KEY_F8) {
            client.setScreen(new ThoriumVideoSettingsScreen(client.screen));
            return;
        }

        if (key == GLFW.GLFW_KEY_F6) {
            boolean newState = !ThoriumGuiManager.isHudEnabled();
            ThoriumGuiManager.setHudEnabled(newState);
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("§2[§aThorium§2] Nuclear Performance HUD: " + (newState ? "§aON" : "§cOFF")), true);
            }
            return;
        }

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key == GLFW.GLFW_KEY_F7 || (key == GLFW.GLFW_KEY_F10 && shift)) {
            Component notification = ThoriumGuiManager.toggleExtremeFpsMode();
            if (client.player != null) {
                client.player.displayClientMessage(notification, true);
            }
        }
    }
}

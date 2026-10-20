package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hyperionfps.gui.ThoriumGuiManager;
import dev.hyperionfps.gui.ThoriumVideoSettingsScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Modern keyboard shortcuts for Thorium in 1.21.11:
 * - F8: Open Thorium Settings Console
 * - F6: Toggle Nuclear Performance HUD
 * - F7 / Shift+F10: Toggle Extreme FPS Mode
 */
@Mixin(KeyboardHandler.class)
public abstract class LegacyKeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void hyperionfps$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.PRESS) {
            Minecraft client = Minecraft.getInstance();
            if (event.key() == InputConstants.KEY_F8) {
                client.setScreen(new ThoriumVideoSettingsScreen(client.screen));
            } else if (event.key() == InputConstants.KEY_F6) {
                boolean newState = !ThoriumGuiManager.isHudEnabled();
                ThoriumGuiManager.setHudEnabled(newState);
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§2[§aThorium§2] Nuclear Performance HUD: " + (newState ? "§aON" : "§cOFF")), true);
                }
            } else if (event.key() == InputConstants.KEY_F7 || (event.key() == InputConstants.KEY_F10 && (event.modifiers() & InputConstants.MOD_SHIFT) != 0)) {
                Component notification = ThoriumGuiManager.toggleExtremeFpsMode();
                if (client.player != null) {
                    client.player.displayClientMessage(notification, true);
                }
            }
        }
    }
}

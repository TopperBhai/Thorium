package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.gui.ThoriumVideoSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Legacy TitleScreen (Main Menu) mixin to add the dedicated Thorium Settings button.
 */
@Mixin(TitleScreen.class)
public abstract class LegacyTitleScreenMixin extends Screen {

    private Button thorium$btn;

    protected LegacyTitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void thorium$addDedicatedThoriumButton(CallbackInfo ci) {
        int btnW = 124;
        int btnH = 20;
        int btnX = this.width - btnW - 12;
        int btnY = 8;

        this.thorium$btn = Button.builder(
                Component.literal("§aThorium Settings"),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new ThoriumVideoSettingsScreen(this));
                    }
                }
        ).bounds(btnX, btnY, btnW, btnH).build();

        this.addRenderableWidget(this.thorium$btn);
    }

    @Inject(method = "repositionElements()V", at = @At("RETURN"), require = 0)
    private void thorium$onReposition(CallbackInfo ci) {
        if (this.thorium$btn != null) {
            int btnW = 124;
            this.thorium$btn.setX(this.width - btnW - 12);
            this.thorium$btn.setY(8);
        }
    }
}

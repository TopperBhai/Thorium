package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.discord.ThoriumDiscordInvite;
import dev.hyperionfps.gui.ThoriumDiscordScreen;
import dev.hyperionfps.gui.ThoriumVideoSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Legacy TitleScreen (Main Menu) mixin to add the dedicated Thorium Settings button
 * and to offer the one-time community invite.
 */
@Mixin(TitleScreen.class)
public abstract class LegacyTitleScreenMixin extends Screen {

    private Button thorium$btn;

    /** Whether the community invite is still owed to the player. */
    private boolean thorium$inviteWanted;

    /** Whether the invite has already been offered in this session. */
    private boolean thorium$inviteOffered;

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

    /**
     * Decides once per main-menu session whether the community invite is still owed.
     *
     * <p>The decision is cached here so that the per-frame tick hook below never has
     * to touch the (synchronised) configuration store on the render thread.</p>
     */
    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void thorium$prepareCommunityInvite(CallbackInfo ci) {
        this.thorium$inviteWanted = ThoriumDiscordInvite.shouldShow();
        this.thorium$inviteOffered = false;
    }

    /**
     * Offers the community invite once, the first time the main menu ticks.
     *
     * <p>The tick hook is used instead of the init hook because the main menu is
     * still being built while {@code init()} runs - switching the screen from there
     * would replace a screen that is only half constructed. The tick hook runs from
     * the game loop, so the switch happens between two frames.</p>
     */
    @Inject(method = "tick()V", at = @At("HEAD"), require = 0)
    private void thorium$maybeShowCommunityInvite(CallbackInfo ci) {
        if (this.thorium$inviteOffered || !this.thorium$inviteWanted) {
            return;
        }
        this.thorium$inviteOffered = true;
        // Record the invite as shown before the card is opened: if the game is closed
        // while the card is on screen, the invite must not come back on the next
        // launch.
        ThoriumDiscordInvite.markShown();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ThoriumDiscordScreen(this));
        }
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
package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.frame.ThoriumFrametimeTracker;
import dev.hyperionfps.gui.ThoriumGuiManager;
import dev.hyperionfps.gui.ThoriumModernHudRenderer;
import dev.hyperionfps.render.batching.HudBatcher;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.21.11 HUD draw batching boundary & Nuclear HUD presentation hook.
 */
@Mixin(Gui.class)
public abstract class LegacyHudBatchingMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void hyperionfps$beginBatch(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudBatcher.beginHudBatch();
        ThoriumFrametimeTracker.recordFrame();
        ThoriumEntityLodThrottler.onFrame();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void hyperionfps$endBatch(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudBatcher.endHudBatch();
        dev.hyperionfps.batch.ThoriumImmediateBatcher.flushActiveBatch();

        if (ThoriumGuiManager.isHudEnabled()) {
            ThoriumModernHudRenderer.render(guiGraphics, Minecraft.getInstance());
        }
    }
}

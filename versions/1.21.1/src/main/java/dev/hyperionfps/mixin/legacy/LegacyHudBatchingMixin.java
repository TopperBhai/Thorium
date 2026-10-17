package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.render.batching.HudBatcher;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Classic-era HUD draw batching boundary.
 */
@Mixin(Gui.class)
public abstract class LegacyHudBatchingMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void hyperionfps$beginBatch(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudBatcher.beginHudBatch();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void hyperionfps$endBatch(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudBatcher.endHudBatch();
        dev.hyperionfps.batch.ThoriumImmediateBatcher.flushActiveBatch();
    }
}

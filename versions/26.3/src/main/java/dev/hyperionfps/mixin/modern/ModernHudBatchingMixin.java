package dev.hyperionfps.mixin.modern;

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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

/**
 * Modern (26.1+) HUD draw batching boundary & live Nuclear HUD presentation hook.
 */
@Mixin(Hud.class)
public abstract class ModernHudBatchingMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void hyperionfps$beginBatch(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudBatcher.beginHudBatch();
        ThoriumFrametimeTracker.recordFrame();
        ThoriumEntityLodThrottler.onFrame();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hyperionfps$endBatch(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudBatcher.endHudBatch();
        dev.hyperionfps.batch.ThoriumImmediateBatcher.flushActiveBatch();

        if (ThoriumGuiManager.isHudEnabled()) {
            ThoriumModernHudRenderer.render(extractor, Minecraft.getInstance());
        }
    }
}

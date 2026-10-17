package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

/**
 * Suppresses full-screen 1080p vignette blending to save GPU fill-rate.
 */
@Mixin(Gui.class)
public abstract class LegacyVignetteMixin {

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperionfps$suppressVignette(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}

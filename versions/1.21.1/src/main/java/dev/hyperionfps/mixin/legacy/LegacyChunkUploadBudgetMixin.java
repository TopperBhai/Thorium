package dev.hyperionfps.mixin.legacy;

import java.util.Queue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/**
 * Replaces unbudgeted chunk mesh uploads with Thorium's Sodium-inspired upload budgeter,
 * eliminating chunk loading lag spikes and micro-stutters in Minecraft 1.21.1.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class LegacyChunkUploadBudgetMixin {

    @Shadow
    private Queue<Runnable> toUpload;

    @Inject(method = "uploadAllPendingUploads", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$onUploadAllPendingUploads(CallbackInfo ci) {
        ThoriumChunkUploadPacer.runBudgetedUploads(this.toUpload);
        ci.cancel();
    }
}

package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;

/**
 * Modern-era (26.3+) Cloud suppression in Extreme FPS mode.
 */
@Mixin(CloudRenderer.class)
public abstract class ModernCloudsMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$suppressCloudsInExtremeMode(CloudStatus cloudStatus, RenderPass pass, CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
        }
    }
}

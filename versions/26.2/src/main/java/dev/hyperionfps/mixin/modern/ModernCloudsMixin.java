package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.culling.AsyncOcclusionCuller;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * Suppresses cloud geometry rendering in Extreme FPS mode to eliminate vertex overhead in modern Minecraft.
 */
@Mixin(CloudRenderer.class)
public abstract class ModernCloudsMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$suppressCloudsInExtremeMode(
            int i, CloudStatus cloudStatus, float f, int j, Vec3 vec3, long l, float g,
            CallbackInfo ci) {
        if (AsyncOcclusionCuller.isExtremeFpsMode()) {
            ci.cancel();
        }
    }
}

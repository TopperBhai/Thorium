package dev.hyperionfps.mixin.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hyperionfps.audio.ThoriumSoundGovernor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;

/**
 * Beyond-Sodium sound governor mixin to throttle redundant audio channel allocation.
 */
@Mixin(SoundEngine.class)
public abstract class LegacySoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$governDuplicateAudio(SoundInstance soundInstance, CallbackInfo ci) {
        if (ThoriumSoundGovernor.shouldSuppressSound(soundInstance)) {
            ci.cancel();
        }
    }
}

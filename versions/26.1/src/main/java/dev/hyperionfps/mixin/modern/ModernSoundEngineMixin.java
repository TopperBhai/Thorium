package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hyperionfps.audio.ThoriumSoundGovernor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;

/**
 * Modern 26.1 Sound Engine Governor mixin.
 */
@Mixin(SoundEngine.class)
public abstract class ModernSoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true, require = 0)
    private void thorium$governSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (ThoriumSoundGovernor.shouldSuppressSound(sound)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}

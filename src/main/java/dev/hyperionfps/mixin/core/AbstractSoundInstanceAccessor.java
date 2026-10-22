package dev.hyperionfps.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;

/**
 * Gives the sound governor access to the volume of a sound instance.
 *
 * <p>{@link net.minecraft.client.resources.sounds.SoundInstance} exposes
 * {@code getVolume()} but has no setter, and the volume field of the abstract base
 * class is protected. The mob farm governor needs to play the <em>same</em> sound
 * more quietly instead of dropping it, and that is only possible by writing the
 * field.</p>
 *
 * <p>The field is named {@code volume} on every supported Minecraft version, which
 * is why this accessor lives in the shared {@code core} family rather than in an
 * era-specific one. The build-time mixin audit verifies that the field exists on
 * every node.</p>
 */
@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {

    /**
     * Overwrites the volume of this sound instance.
     *
     * @param volume the new volume
     */
    @Accessor("volume")
    void thorium$setVolume(float volume);
}

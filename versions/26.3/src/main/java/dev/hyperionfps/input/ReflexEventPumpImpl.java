package dev.hyperionfps.input;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.hyperionfps.mixin.modern.MinecraftSdlEventHandlerAccessor;
import net.minecraft.client.Minecraft;

/**
 * The 26.3 platform drain for the late input sampler.
 *
 * <p>26.3 moved the windowing layer from GLFW to SDL3. The game's own drain is
 * {@code RenderSystem.pollEvents(SDLEventHandler)}, and it needs the handler that
 * dispatches the SDL events into the game's callbacks - the handler lives in a
 * private field of the client, so it is read through
 * {@link MinecraftSdlEventHandlerAccessor}.</p>
 *
 * <p>This is the exact routine the game calls at the top of its main loop, so the
 * late sample uses the same entry point - including the watchdog the game keeps
 * around it.</p>
 *
 * <p>The events are delivered exactly once - the queue is drained, not copied - so
 * the only difference to vanilla is the <em>moment</em> at which they arrive: earlier
 * in the frame, while the camera matrix is still being built.</p>
 */
public final class ReflexEventPumpImpl implements ReflexEventPump {

    @Override
    public void pump() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        RenderSystem.pollEvents(((MinecraftSdlEventHandlerAccessor) client).thorium$sdlEventHandler());
    }
}

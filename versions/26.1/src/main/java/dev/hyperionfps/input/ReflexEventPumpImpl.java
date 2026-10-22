package dev.hyperionfps.input;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * The 26.1 platform drain for the late input sampler.
 *
 * <p>26.1 still runs on GLFW, and the game's own drain is the <em>public</em>
 * {@code RenderSystem#pollEvents()}. It is the exact routine the game calls at the
 * top of its main loop, so the late sample uses the same entry point - including the
 * watchdog the game keeps around it.</p>
 *
 * <p>The events are delivered exactly once - the queue is drained, not copied - so
 * the only difference to vanilla is the <em>moment</em> at which they arrive: earlier
 * in the frame, while the camera matrix is still being built.</p>
 */
public final class ReflexEventPumpImpl implements ReflexEventPump {

    @Override
    public void pump() {
        RenderSystem.pollEvents();
    }
}

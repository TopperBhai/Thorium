package dev.hyperionfps.input;

import org.lwjgl.glfw.GLFW;

/**
 * The 1.21.11 platform drain for the late input sampler.
 *
 * <p>1.21.11 still runs on GLFW, and the game's own drain lives in a <em>private</em>
 * {@code RenderSystem#pollEvents()} that this source may not call. The GLFW routine
 * underneath it is public, and it is exactly what the game does: dispatch every
 * pending window, cursor and keyboard event to the callbacks the game registered.</p>
 *
 * <p>The events are delivered exactly once - the queue is drained, not copied - so
 * the only difference to vanilla is the <em>moment</em> at which they arrive: earlier
 * in the frame, while the camera matrix is still being built.</p>
 */
public final class ReflexEventPumpImpl implements ReflexEventPump {

    @Override
    public void pump() {
        GLFW.glfwPollEvents();
    }
}

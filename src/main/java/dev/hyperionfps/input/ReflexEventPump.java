package dev.hyperionfps.input;

/**
 * The platform half of Thorium's late input sampling.
 *
 * <p>Minecraft never asks the operating system for input at a convenient moment: it
 * drains the platform event queue at one fixed point of the frame and then works with
 * whatever it got. Everything the player does between that drain and the end of the
 * frame has to wait for the <em>next</em> frame.</p>
 *
 * <p>This interface is the seam between the version-independent policy
 * ({@link ThoriumReflexController}) and the version-specific way of draining that
 * queue:</p>
 * <ul>
 *   <li>1.21.1 and 1.21.11 drain it through {@code GLFW.glfwPollEvents()},</li>
 *   <li>26.1 and 26.2 through the public {@code RenderSystem.pollEvents()},</li>
 *   <li>26.3 through {@code RenderSystem.pollEvents(SDLEventHandler)}, because the
 *       26.3 nodes moved the windowing layer from GLFW to SDL3.</li>
 * </ul>
 *
 * <p>Every implementation lives in the version-specific source tree under the fixed
 * name {@code dev.hyperionfps.input.ReflexEventPumpImpl}; the shared controller finds
 * it by name at client initialisation, so a node that cannot pump its event queue
 * simply never gets the feature - it is never wired, and the settings console says
 * so.</p>
 */
public interface ReflexEventPump {

    /**
     * The pump used when no platform pump is available.
     *
     * <p>It is a non-capturing lambda, so the JVM caches the instance and the frame
     * path never allocates.</p>
     */
    ReflexEventPump NONE = () -> {
    };

    /**
     * Delivers every platform event that arrived since the last drain.
     *
     * <p>Called on the render thread, immediately before the game applies the pending
     * mouse movement to the camera. An implementation must never throw: the caller
     * guards against it, but a pump that fails silently is better than one that
     * breaks the frame.</p>
     */
    void pump();
}

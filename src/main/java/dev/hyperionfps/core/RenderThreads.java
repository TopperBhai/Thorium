package dev.hyperionfps.core;

/**
 * Holds the live render (client) thread reference, captured the moment the
 * Minecraft client instance is constructed by
 * {@code dev.hyperionfps.mixin.core.MinecraftThreadCaptureMixin}.
 *
 * <p>This is the anchor every later optimization stage builds on: thread
 * priority tuning, frame pacing, and hand-off of occlusion work to background
 * workers all need to know exactly which thread owns the frame loop.</p>
 */
public final class RenderThreads {

    private static volatile Thread clientThread;

    private RenderThreads() {
    }

    /** Invoked once from the client-construction mixin on the render thread itself. */
    public static void captureClientThread() {
        if (clientThread == null) {
            clientThread = Thread.currentThread();
            ThreadGovernor.tuneRenderThread(clientThread);
        }
    }

    /** The captured render thread, or {@code null} if capture was skipped. */
    public static Thread clientThread() {
        return clientThread;
    }

    public static boolean isCaptured() {
        return clientThread != null;
    }

    public static boolean isOnClientThread() {
        return clientThread != null && clientThread == Thread.currentThread();
    }
}

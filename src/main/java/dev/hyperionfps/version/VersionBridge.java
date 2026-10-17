package dev.hyperionfps.version;

/**
 * Universal abstraction layer for Minecraft engine differences across versions.
 *
 * <p>Bridges version and pipeline disparities across:
 * <ul>
 *   <li><b>Window &amp; GL Context:</b> GLFW swap intervals, native window handles, frame rate uncapping.</li>
 *   <li><b>Rendering Pipeline:</b> Immediate batching flags, render distance scaling, Extreme FPS mode.</li>
 *   <li><b>Entity Management:</b> Frustum &amp; occlusion culling queries, dynamic distance scaling.</li>
 *   <li><b>FastMath Acceleration:</b> Cache-line precomputed trigonometric lookup tables.</li>
 * </ul>
 *
 * All methods are thread-safe and non-allocating.
 */
public interface VersionBridge {

    /** Parsed version of the running game. Never {@code null}. */
    GameVersion gameVersion();

    /** Architecture era (see {@link Era}). */
    Era era();

    /** Runtime namespace: {@code "intermediary"}, {@code "mojmap"}, or {@code "unknown"}. */
    String runtimeNamespace();

    /** True when the game ships unobfuscated (&gt;= 26.1). */
    boolean isUnobfuscatedEra();

    /** {@code gameVersion >= version} using any parseable version string. */
    boolean atLeast(String version);

    /** Window and OpenGL swapchain context operations. */
    WindowContext windowContext();

    /** Rendering pipeline controls and batching capability query. */
    RenderBridge renderBridge();

    /** Entity, block entity, and particle occlusion management. */
    EntityBridge entityBridge();

    /** Pre-calculated high-speed math operations (FastMath / Riven's lookup). */
    MathBridge mathBridge();

    /** Whether another mod is present (e.g. Sodium/Iris interop decisions). */
    boolean isModLoaded(String modId);

    /** Existence probe for a class resource path, e.g. {@code "some/pkg/Class.class"}. */
    boolean hasResource(String resourcePath);

    /** One-line human readable summary for logs and the in-game overlay. */
    String describe();

    // ------------------------------------------------------------------------
    // Sub-bridges
    // ------------------------------------------------------------------------

    interface WindowContext {
        long getWindowHandle();
        void setWindowHandle(long handle);
        boolean setSwapInterval(int interval);
        int getSwapInterval();
        boolean isVsyncActive();
        int getFramebufferWidth();
        int getFramebufferHeight();
        void updateDimensions(int width, int height);
        void setRawFramerateLimit(int maxFps);
        int getRawFramerateLimit();
    }

    interface RenderBridge {
        boolean isBatchingSupported();
        boolean isModernGpuPipeline();
        int getRenderDistance();
        void setRenderDistance(int chunks);
        boolean isExtremeFpsMode();
        void setExtremeFpsMode(boolean enabled);
        void reloadRenderers();
    }

    interface EntityBridge {
        boolean isCullingEnabled();
        void setCullingEnabled(boolean enabled);
        boolean shouldCullEntity(Object entity, double camX, double camY, double camZ);
        boolean shouldCullBlockEntity(Object blockEntity, double camX, double camY, double camZ);
        boolean shouldCullParticle(double x, double y, double z);
        double getDynamicDistanceScaling();
        void setDynamicDistanceScaling(double scale);
    }

    interface MathBridge {
        float sin(float rad);
        float cos(float rad);
        float atan2(float y, float x);
        float invSqrt(float x);
        float sqrt(float x);
    }
}

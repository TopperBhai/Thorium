package dev.hyperionfps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hyperionfps.config.ThoriumConfig;
import dev.hyperionfps.core.RenderThreads;
import dev.hyperionfps.core.ThoriumCompatibility;
import dev.hyperionfps.core.ThoriumSafety;
import dev.hyperionfps.core.ThoriumSubsystemReport;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.mixin.HyperionMixinPlugin;
import dev.hyperionfps.version.VersionBridge;
import dev.hyperionfps.version.VersionBridgeRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Main client entrypoint for Thorium Optimization.
 */
public final class HyperionFPS implements ClientModInitializer {

    public static final String MOD_ID = "thorium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Thorium");

    @Override
    public void onInitializeClient() {
        VersionBridge bridge = VersionBridgeRegistry.get();

        LOGGER.info("Thorium (Th-90) {} | {}", modVersion(), bridge.describe());
        LOGGER.info("Mixin router status: {}", HyperionMixinPlugin.status());
        LOGGER.info("Render thread captured: {}",
                RenderThreads.isCaptured() ? "yes -> '" + RenderThreads.clientThread().getName() + "'" : "pending");

        // Safe mode short-circuits every optimisation. The mixins were already
        // skipped by the router; this also keeps the background subsystems dormant.
        if (ThoriumSafety.isSafeMode()) {
            LOGGER.warn("Thorium SAFE MODE active - no optimisations are running.");
            return;
        }

        // Restore persisted user settings (hardware preset, toggles, throttle rates)
        // before the first frame so the player never sees the default profile flash.
        try {
            ThoriumConfig.get().applyToSubsystems();
        } catch (Throwable failure) {
            LOGGER.warn("Could not restore persisted Thorium settings ({}); using defaults",
                    failure.toString());
        }

        // Initialize Phase 3 Async Occlusion Culling Engine
        AsyncOcclusionCuller.init();
        LOGGER.info("Async Occlusion Culling Engine online: daemon thread running");

        // The AFK power saver is live from here on: it hooks the mouse and keyboard
        // inputs and parks the render thread while the player is away.
        try {
            dev.hyperionfps.frame.ThoriumAfkManager.install();
        } catch (Throwable failure) {
            LOGGER.warn("AFK power saver could not be installed: {}", failure.toString());
        }

        // Probe the mods that Thorium has to coexist with (shader packs, competing
        // renderers) before anything else can be switched on.
        try {
            ThoriumCompatibility.detect(ThoriumConfig.get().getBoolean(ThoriumConfig.KEY_COMPAT_AUTO_YIELD, true));
            ThoriumCompatibility.applyYieldToCompetingRenderer();
        } catch (Throwable failure) {
            LOGGER.warn("Compatibility probe failed: {}", failure.toString());
        }

        // Connect the adaptive render-distance governor to the game's own option. The
        // governor yields whenever the player changes the distance by hand.
        try {
            dev.hyperionfps.adaptive.RenderDistanceGovernor.install();
        } catch (Throwable failure) {
            LOGGER.warn("Adaptive render distance governor could not be attached: {}", failure.toString());
        }

        // Initialize Beyond-Sodium Async Light Engine
        dev.hyperionfps.lighting.AsyncLightEngine.getInstance().init();

        // Initialize Advanced Fabric API subsystems if available
        boolean fabricHooksLoaded = false;
        try {
            Class<?> fabricHooks = Class.forName("dev.hyperionfps.fabric.ThoriumFabricHooks");
            fabricHooks.getMethod("init").invoke(null);
            fabricHooksLoaded = true;
        } catch (Throwable ignored) {
            LOGGER.debug("Fabric API advanced hooks not loaded (standalone mode)");
        }
        LOGGER.info("Fabric API hooks: {}", fabricHooksLoaded ? "integrated" : "standalone");

        LOGGER.info("Subsystem states:\n{}", ThoriumSubsystemReport.format());
    }

    /** Process-wide version/capability bridge. */
    public static VersionBridge bridge() {
        return VersionBridgeRegistry.get();
    }

    /** Human readable version of this mod, or {@code "development"} outside a loader. */
    public static String modVersion() {
        try {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("development");
        } catch (Throwable unavailable) {
            return "development";
        }
    }
}

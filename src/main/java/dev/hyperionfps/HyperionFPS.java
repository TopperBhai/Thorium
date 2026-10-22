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

        // The storage culler is live from here on: the block-entity and entity
        // culling hooks consult it for every chest, shulker box and item frame.
        try {
            dev.hyperionfps.culling.StorageCullingEngine.install();
        } catch (Throwable failure) {
            LOGGER.warn("Storage culler could not be installed: {}", failure.toString());
        }

        // The Stutter Hunter needs the JVM's garbage-collection notifications to
        // tell a collection pause apart from a rendering problem.
        try {
            dev.hyperionfps.telemetry.ThoriumGcWatcher.install();
            dev.hyperionfps.telemetry.ThoriumStutterHunter.install();
        } catch (Throwable failure) {
            LOGGER.warn("Stutter hunter could not be installed: {}", failure.toString());
        }

        // The mob farm optimizer is live from here on: the entity render hooks
        // record every living entity in the cluster map and ask it whether the
        // animation of a mob may be skipped.
        try {
            dev.hyperionfps.anim.MobFarmOptimizer.install();
        } catch (Throwable failure) {
            LOGGER.warn("Mob farm optimizer could not be installed: {}", failure.toString());
        }

        // The explosion shield is live from here on: the particle creation hook
        // consults it for every explosion, firework and smoke particle.
        try {
            dev.hyperionfps.particle.ExplosionShield.install();
        } catch (Throwable failure) {
            LOGGER.warn("Explosion shield could not be installed: {}", failure.toString());
        }

        // The chunk upload pacer is live from here on: the chunk hooks consult it for
        // every section that is built, and the Elytra Flight Smoother inside it
        // prioritises the sections the player is flying towards.
        try {
            dev.hyperionfps.chunk.ThoriumChunkUploadPacer.install();
        } catch (Throwable failure) {
            LOGGER.warn("Chunk upload pacer could not be installed: {}", failure.toString());
        }

        // The late input sampler is live from here on: the mouse hook drains the
        // platform event queue at the tail of the frame, so the movement the player
        // made while the frame was working is applied to the frame they are looking
        // at instead of the next one.
        try {
            dev.hyperionfps.input.ThoriumReflexController.install();
        } catch (Throwable failure) {
            LOGGER.warn("Late input sampler could not be installed: {}", failure.toString());
        }

        // The No-Lag Weather and the Clean Water / Fast Fog optimiser are live from
        // here on: the weather hooks consult it for every precipitation pass and the
        // particle hook for every splash particle.
        try {
            dev.hyperionfps.render.WeatherFogOptimizer.install();
        } catch (Throwable failure) {
            LOGGER.warn("Weather and fog optimiser could not be installed: {}", failure.toString());
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

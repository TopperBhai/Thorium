package dev.hyperionfps.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects the mods Thorium has to coexist with, and decides which of its own
 * optimisations must stand down.
 *
 * <h2>Why this matters</h2>
 * <ul>
 *   <li><b>Competing renderers</b> (Sodium, Embeddium and friends) perform their own
 *       chunk, entity and particle culling. Running both means double culling,
 *       contradictory visibility decisions and doubled raycast cost - and it is the
 *       most common cause of "Thorium broke my game" reports.</li>
 *   <li><b>Shader packs</b> (Iris, Oculus, OptiFine) depend on the exact precision of
 *       vanilla's math helpers. Thorium's fast-math overrides replace
 *       {@code Mth.sin}, {@code Mth.cos}, {@code Mth.atan2} and {@code Mth.invSqrt}
 *       with lookup-table approximations, which is a visible downgrade under a shader
 *       pack and a source of client-prediction drift.</li>
 * </ul>
 *
 * <p>The detection runs once, at client initialisation, and the result is cached. It
 * never throws: a missing or broken Fabric loader simply means "nothing detected".</p>
 */
public final class ThoriumCompatibility {

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/Compatibility");

    /** Mod ids that ship a competing chunk/entity/particle renderer. */
    private static final List<String> COMPETING_RENDERERS =
            List.of("sodium", "embeddium", "rubidium", "magnesium", "lithium", "oclite", "vibrant");

    /** Mod ids that ship a shader pack or shader-capable renderer. */
    private static final List<String> SHADER_PACKS =
            List.of("oculus", "iris", "optifine", "continuity");

    private static final AtomicBoolean FAST_MATH_ENABLED = new AtomicBoolean(false);
    private static volatile boolean competingRenderer;
    private static volatile boolean shaderPack;
    private static volatile boolean autoYield = true;
    private static volatile boolean yieldApplied;

    private ThoriumCompatibility() {
    }

    /**
     * Probes the loaded mods. Call once during client initialisation.
     *
     * @param autoYield whether Thorium should stand down when a competing renderer is
     *                  present
     */
    public static void detect(boolean autoYield) {
        ThoriumCompatibility.autoYield = autoYield;
        competingRenderer = anyLoaded(COMPETING_RENDERERS);
        shaderPack = anyLoaded(SHADER_PACKS);

        if (shaderPack) {
            // Shader packs rely on vanilla's math precision - the fast-math overrides
            // are approximations and must not run next to them.
            if (FAST_MATH_ENABLED.getAndSet(false)) {
                LOGGER.warn("A shader pack is loaded - Thorium's fast-math overrides were disabled.");
            }
            LOGGER.info("Shader pack detected: fast-math overrides are off.");
        }
        if (competingRenderer) {
            LOGGER.info("Competing renderer detected - Thorium yields the overlapping culling "
                    + "subsystems (auto-yield {}).", autoYield ? "on" : "off");
        }
    }

    private static boolean anyLoaded(List<String> modIds) {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            for (String modId : modIds) {
                if (loader.isModLoaded(modId)) {
                    return true;
                }
            }
        } catch (Throwable unavailable) {
            // Outside a Fabric loader nothing can be detected; assume a clean environment.
        }
        return false;
    }

    /** Whether a competing renderer (Sodium family) is loaded. */
    public static boolean hasCompetingRenderer() {
        return competingRenderer;
    }

    /** Whether a shader pack is loaded. */
    public static boolean hasShaderPack() {
        return shaderPack;
    }

    /** Whether Thorium's fast-math overrides may run. */
    public static boolean isFastMathEnabled() {
        return FAST_MATH_ENABLED.get();
    }

    /** Enables or disables the fast-math overrides. */
    public static void setFastMathEnabled(boolean enabled) {
        FAST_MATH_ENABLED.set(enabled && !shaderPack);
    }

    /** Whether Thorium stands down for a competing renderer. */
    public static boolean isAutoYieldEnabled() {
        return autoYield;
    }

    /** Enables or disables the stand-down for a competing renderer. */
    public static void setAutoYieldEnabled(boolean enabled) {
        autoYield = enabled;
    }

    /** Whether the competing-renderer stand-down has already been applied. */
    public static boolean isYieldApplied() {
        return yieldApplied;
    }

    /**
     * Disables the subsystems that a competing renderer already covers.
     *
     * <p>Called once during client initialisation. Frame pacing, telemetry, the sound
     * governor and the texture throttler stay enabled - they do not overlap with any
     * other renderer.</p>
     */
    public static void applyYieldToCompetingRenderer() {
        if (!autoYield || !competingRenderer || yieldApplied) {
            return;
        }
        yieldApplied = true;
        try {
            dev.hyperionfps.culling.AsyncOcclusionCuller.setEnabled(false);
            dev.hyperionfps.culling.OcclusionGrid.setEnabled(false);
            dev.hyperionfps.culling.ThoriumBvhCuller.setEnabled(false);
            dev.hyperionfps.culling.SectionCullingEngine.setEnabled(false);
            dev.hyperionfps.particle.ThoriumParticleBudget.setEnabled(false);
            LOGGER.info("Yielded entity/block-entity/particle culling, the occlusion grid, the BVH "
                    + "cluster culler and the section culler to the competing renderer.");
        } catch (Throwable failure) {
            LOGGER.warn("Could not yield the culling subsystems: {}", failure.toString());
        }
    }
}

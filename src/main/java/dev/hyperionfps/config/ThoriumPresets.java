package dev.hyperionfps.config;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.audio.ThoriumSoundGovernor;
import dev.hyperionfps.batch.ThoriumImmediateBatcher;
import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import dev.hyperionfps.core.ThoriumCompatibility;
import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import dev.hyperionfps.frame.ThoriumFramePacer;
import dev.hyperionfps.input.ThoriumInputPacer;
import dev.hyperionfps.particle.ThoriumParticleBudget;
import dev.hyperionfps.texture.ThoriumTextureThrottler;

/**
 * The three hardware presets, applied from the settings console.
 *
 * <p>A preset is a <em>complete profile</em>: it sets every switch it owns, so the
 * player never has to guess which combination the mod considers "balanced". The
 * preset logic used to live in each of the five per-version
 * {@code ThoriumVideoSettingsScreen} classes, which meant five copies that could
 * drift apart. It lives here instead, in shared source, so all five version nodes
 * apply exactly the same profile (NFR-7).</p>
 *
 * <h2>What a preset deliberately does not touch</h2>
 * <ul>
 *   <li><b>Safe mode</b> - an emergency escape hatch is never switched off by a
 *       preset.</li>
 *   <li><b>The adaptive render distance</b> - it rewrites a vanilla option and is
 *       opt-in; a preset must not silently enable it.</li>
 *   <li><b>The HUD</b> - that is a player preference, not a performance profile.</li>
 * </ul>
 */
public final class ThoriumPresets {

    /** Aggressive profile for integrated GPUs and low-spec laptops. */
    public static final String POTATO = "POTATO";

    /** The default profile: smooth 1 % lows with full vanilla fidelity. */
    public static final String BALANCED = "BALANCED";

    /** Maximum throughput profile for a dedicated GPU. */
    public static final String NUCLEAR = "NUCLEAR";

    /** Set when the player changes a single switch away from a preset. */
    public static final String CUSTOM = "CUSTOM";

    private ThoriumPresets() {
    }

    /**
     * Normalises a preset name.
     *
     * <p>Anything unknown becomes {@link #BALANCED}, so a hand-edited config file
     * can never leave the mod in a half-applied state.</p>
     */
    public static String normalise(String preset) {
        if (preset == null) {
            return BALANCED;
        }
        String trimmed = preset.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (trimmed) {
            case POTATO, BALANCED, NUCLEAR -> trimmed;
            default -> BALANCED;
        };
    }

    /**
     * Applies a preset to every subsystem and persists the result.
     *
     * <p>Never throws: a misbehaving subsystem must not leave the console in a
     * broken state, so each call is guarded.</p>
     */
    public static void apply(String preset) {
        String normalised = normalise(preset);
        try {
            switch (normalised) {
                case POTATO -> applyPotato();
                case NUCLEAR -> applyNuclear();
                default -> applyBalanced();
            }
        } catch (Throwable failure) {
            org.slf4j.LoggerFactory.getLogger("Thorium/Presets")
                    .warn("Could not apply the {} preset ({}); keeping the current profile",
                            normalised, failure.toString());
        }
        ThoriumConfig.get().setString(ThoriumConfig.KEY_PRESET, normalised);
        ThoriumConfig.get().saveNow();
    }

    private static void applyPotato() {
        AsyncOcclusionCuller.setExtremeFpsMode(true);
        ThoriumTextureThrottler.setEnabled(true);
        ThoriumTextureThrottler.setThrottleDivider(4);
        ThoriumImmediateBatcher.setEnabled(true);
        ThoriumEntityLodThrottler.setEnabled(true);
        ThoriumBvhCuller.setEnabled(true);
        ThoriumSoundGovernor.setEnabled(true);
        ThoriumParticleBudget.setEnabled(true);
        ThoriumInputPacer.setEnabled(true);
        ThoriumFramePacer.setEnabled(true);
        ThoriumChunkUploadPacer.setEnabled(true);
        ThoriumCompatibility.setFastMathEnabled(false);

        // Headline features: maximum load shedding, minimum diagnostics.
        ThoriumFeatureToggles.setMobFarmEnabled(true);
        ThoriumFeatureToggles.setStorageItemFramesEnabled(true);
        ThoriumFeatureToggles.setElytraSmootherEnabled(true);
        ThoriumFeatureToggles.setAfkEcoEnabled(true);
        ThoriumFeatureToggles.setAfkTimeoutSeconds(30);
        ThoriumFeatureToggles.setStutterAlertEnabled(false);
        ThoriumFeatureToggles.setExplosionShieldEnabled(true);
        ThoriumFeatureToggles.setReflexEnabled(false);
        ThoriumFeatureToggles.setWeatherMode(ThoriumFeatureToggles.WeatherMode.OFF);
        ThoriumFeatureToggles.setCleanWaterFogEnabled(true);
    }

    private static void applyBalanced() {
        AsyncOcclusionCuller.setExtremeFpsMode(false);
        ThoriumTextureThrottler.setEnabled(true);
        ThoriumTextureThrottler.setThrottleDivider(2);
        ThoriumImmediateBatcher.setEnabled(true);
        ThoriumEntityLodThrottler.setEnabled(true);
        ThoriumBvhCuller.setEnabled(true);
        ThoriumSoundGovernor.setEnabled(true);
        ThoriumParticleBudget.setEnabled(true);
        ThoriumInputPacer.setEnabled(true);
        ThoriumFramePacer.setEnabled(true);
        ThoriumChunkUploadPacer.setEnabled(true);
        ThoriumCompatibility.setFastMathEnabled(false);

        // Headline features: every optimisation on, diagnostics on, visuals kept.
        ThoriumFeatureToggles.setMobFarmEnabled(true);
        ThoriumFeatureToggles.setStorageItemFramesEnabled(true);
        ThoriumFeatureToggles.setElytraSmootherEnabled(true);
        ThoriumFeatureToggles.setAfkEcoEnabled(true);
        ThoriumFeatureToggles.setAfkTimeoutSeconds(
                ThoriumFeatureToggles.DEFAULT_AFK_TIMEOUT_SECONDS);
        ThoriumFeatureToggles.setStutterAlertEnabled(true);
        ThoriumFeatureToggles.setExplosionShieldEnabled(true);
        ThoriumFeatureToggles.setReflexEnabled(false);
        ThoriumFeatureToggles.setWeatherMode(ThoriumFeatureToggles.WeatherMode.MINIMAL);
        ThoriumFeatureToggles.setCleanWaterFogEnabled(true);
    }

    private static void applyNuclear() {
        AsyncOcclusionCuller.setExtremeFpsMode(false);
        ThoriumTextureThrottler.setEnabled(true);
        ThoriumTextureThrottler.setThrottleDivider(1);
        ThoriumImmediateBatcher.setEnabled(true);
        ThoriumEntityLodThrottler.setEnabled(true);
        ThoriumBvhCuller.setEnabled(true);
        ThoriumSoundGovernor.setEnabled(true);
        ThoriumParticleBudget.setEnabled(true);
        ThoriumInputPacer.setEnabled(true);
        ThoriumFramePacer.setEnabled(true);
        ThoriumChunkUploadPacer.setEnabled(true);
        ThoriumCompatibility.setFastMathEnabled(true);

        // Headline features: every optimisation on, lowest latency, no weather.
        ThoriumFeatureToggles.setMobFarmEnabled(true);
        ThoriumFeatureToggles.setStorageItemFramesEnabled(true);
        ThoriumFeatureToggles.setElytraSmootherEnabled(true);
        ThoriumFeatureToggles.setAfkEcoEnabled(true);
        ThoriumFeatureToggles.setAfkTimeoutSeconds(120);
        ThoriumFeatureToggles.setStutterAlertEnabled(true);
        ThoriumFeatureToggles.setExplosionShieldEnabled(true);
        ThoriumFeatureToggles.setReflexEnabled(true);
        ThoriumFeatureToggles.setWeatherMode(ThoriumFeatureToggles.WeatherMode.OFF);
        ThoriumFeatureToggles.setCleanWaterFogEnabled(true);
    }

    /**
     * Whether the adaptive render distance is currently switched on.
     *
     * <p>Exposed so the settings console can explain why a preset did not change
     * it.</p>
     */
    public static boolean isAdaptiveDistanceEnabled() {
        return AdaptiveDistanceController.getInstance().isEnabled();
    }
}

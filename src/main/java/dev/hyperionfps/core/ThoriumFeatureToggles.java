package dev.hyperionfps.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry of Thorium's player-facing <em>headline feature</em> switches.
 *
 * <p>The headline features - AFK Eco, the Stutter Hunter, the Mob Farm Optimizer, the
 * Explosion Shield, the Elytra Smoother, Reflex, the weather modes and the storage
 * culling - are delivered as a series of independent steps. Their <b>configuration</b>
 * therefore lives here, in one place, instead of being spread across eight subsystem
 * classes that do not exist yet. Every later step reads its switch from this registry,
 * so the settings console, the hardware presets and {@code thorium-config.json} all
 * agree on exactly one value (NFR-7: single source of truth).</p>
 *
 * <h2>Why a registry and not one flag per subsystem class</h2>
 * <ul>
 *   <li>The settings console and the presets need the flags <em>before</em> the
 *       subsystems exist; otherwise the console would have to show a switch that
 *       cannot do anything.</li>
 *   <li>When a subsystem lands it does not need to <em>own</em> the flag - it reads
 *       it from here - so there is never a second copy that can drift.</li>
 * </ul>
 *
 * <h2>Wired vs. enabled</h2>
 * <p>A feature is only marked {@linkplain Feature#isWired() wired} once its
 * implementation has a live call site in the game. Until then the settings console
 * shows it as <em>not available</em> instead of pretending the switch does something
 * (REQ-UI-2: no fake toggles).</p>
 *
 * <p>Every field is a plain atomic or volatile - reading a flag never blocks, and the
 * registry never allocates (NFR-1, NFR-2).</p>
 */
public final class ThoriumFeatureToggles {

    /**
     * Weather rendering modes for REQ-HEAD-8.
     *
     * <p>{@link #VANILLA} keeps the stock rain sheets and ground splashes,
     * {@link #MINIMAL} draws a single cheap precipitation pass and suppresses the
     * splash particle flood, and {@link #OFF} removes the precipitation layer from the
     * client while the world and the server keep their weather state untouched.</p>
     */
    public enum WeatherMode {
        VANILLA,
        MINIMAL,
        OFF;

        /** Parses a persisted value leniently; anything unknown becomes {@link #MINIMAL}. */
        public static WeatherMode parse(String raw) {
            if (raw == null) {
                return MINIMAL;
            }
            String trimmed = raw.trim().toUpperCase(java.util.Locale.ROOT);
            for (WeatherMode mode : values()) {
                if (mode.name().equals(trimmed)) {
                    return mode;
                }
            }
            return MINIMAL;
        }

        /** The next mode in the cycle used by the settings console. */
        public WeatherMode next() {
            return switch (this) {
                case VANILLA -> MINIMAL;
                case MINIMAL -> OFF;
                case OFF -> VANILLA;
            };
        }
    }

    /**
     * The headline features, in the order the settings console presents them.
     *
     * <p>Each constant carries its own state so that the registry needs no map
     * lookups on the frame path.</p>
     */
    public enum Feature {
        /** REQ-HEAD-1 - dense mob clusters get their limb animation throttled. */
        MOB_FARM("anim.mob_farm.enabled", "mob-farm-optimizer"),
        /** REQ-HEAD-2 - chests, shulkers and item frames are culled aggressively. */
        STORAGE_ITEM_FRAMES("culling.storage_item_frames.enabled", "storage-item-frame-culling"),
        /** REQ-HEAD-3 - chunk uploads are prioritised along the flight vector. */
        ELYTRA_SMOOTHER("chunk.elytra_smoother.enabled", "elytra-flight-smoother"),
        /** REQ-HEAD-4 - the client throttles itself to 10 FPS while the player is away. */
        AFK_ECO("frame.afk_eco.enabled", "afk-eco"),
        /** REQ-HEAD-5 - a freeze is attributed to its dominant phase and toasted. */
        STUTTER_ALERT("telemetry.stutter_alert.enabled", "stutter-alert"),
        /** REQ-HEAD-6 - TNT and firework particle floods are capped. */
        EXPLOSION_SHIELD("particle.explosion_shield.enabled", "explosion-shield"),
        /** REQ-HEAD-7 - mouse and keyboard are sampled at the tail of the frame. */
        REFLEX("input.reflex.enabled", "ultra-low-latency-reflex"),
        /** REQ-HEAD-9 - underwater fog is clamped and the fog curve simplified. */
        CLEAN_WATER_FOG("render.clean_water_fog.enabled", "clean-water-fog");

        private final String configKey;
        private final String reportName;
        private final AtomicBoolean enabled = new AtomicBoolean(true);
        private volatile boolean wired;

        Feature(String configKey, String reportName) {
            this.configKey = configKey;
            this.reportName = reportName;
        }

        /** The key this feature is persisted under in {@code thorium-config.json}. */
        public String configKey() {
            return configKey;
        }

        /** The name this feature is listed under in the startup subsystem report. */
        public String reportName() {
            return reportName;
        }

        /** Whether the feature currently has a live implementation. */
        public boolean isWired() {
            return wired;
        }

        /** Whether the player has the feature switched on. */
        public boolean isEnabled() {
            return enabled.get();
        }

        void setEnabled(boolean value) {
            enabled.set(value);
        }

        /**
         * Marks this feature as implemented.
         *
         * <p>Called by the feature's own subsystem once it has a call site in the
         * game. From that moment on the settings console offers it as a working
         * switch instead of "not available".</p>
         */
        public void markWired() {
            this.wired = true;
        }
    }

    /** Default inactivity timeout before AFK Eco engages, in seconds. */
    public static final int DEFAULT_AFK_TIMEOUT_SECONDS = 60;

    /** Shortest inactivity timeout the console allows, in seconds. */
    public static final int MIN_AFK_TIMEOUT_SECONDS = 5;

    /** Longest inactivity timeout the console allows, in seconds. */
    public static final int MAX_AFK_TIMEOUT_SECONDS = 3600;

    private static final AtomicInteger AFK_TIMEOUT_SECONDS =
            new AtomicInteger(DEFAULT_AFK_TIMEOUT_SECONDS);
    private static final AtomicReference<WeatherMode> WEATHER_MODE =
            new AtomicReference<>(WeatherMode.MINIMAL);

    private ThoriumFeatureToggles() {
    }

    // --------------------------------------------------------------------
    // Generic access
    // --------------------------------------------------------------------

    /** Whether the given feature is switched on. */
    public static boolean isEnabled(Feature feature) {
        return feature.isEnabled();
    }

    /** Switches the given feature on or off. */
    public static void setEnabled(Feature feature, boolean value) {
        feature.setEnabled(value);
    }

    // --------------------------------------------------------------------
    // Named accessors
    // --------------------------------------------------------------------

    public static boolean isMobFarmEnabled() {
        return Feature.MOB_FARM.isEnabled();
    }

    public static void setMobFarmEnabled(boolean value) {
        Feature.MOB_FARM.setEnabled(value);
    }

    public static boolean isStorageItemFramesEnabled() {
        return Feature.STORAGE_ITEM_FRAMES.isEnabled();
    }

    public static void setStorageItemFramesEnabled(boolean value) {
        Feature.STORAGE_ITEM_FRAMES.setEnabled(value);
    }

    public static boolean isElytraSmootherEnabled() {
        return Feature.ELYTRA_SMOOTHER.isEnabled();
    }

    public static void setElytraSmootherEnabled(boolean value) {
        Feature.ELYTRA_SMOOTHER.setEnabled(value);
    }

    public static boolean isAfkEcoEnabled() {
        return Feature.AFK_ECO.isEnabled();
    }

    public static void setAfkEcoEnabled(boolean value) {
        Feature.AFK_ECO.setEnabled(value);
    }

    public static boolean isStutterAlertEnabled() {
        return Feature.STUTTER_ALERT.isEnabled();
    }

    public static void setStutterAlertEnabled(boolean value) {
        Feature.STUTTER_ALERT.setEnabled(value);
    }

    public static boolean isExplosionShieldEnabled() {
        return Feature.EXPLOSION_SHIELD.isEnabled();
    }

    public static void setExplosionShieldEnabled(boolean value) {
        Feature.EXPLOSION_SHIELD.setEnabled(value);
    }

    public static boolean isReflexEnabled() {
        return Feature.REFLEX.isEnabled();
    }

    public static void setReflexEnabled(boolean value) {
        Feature.REFLEX.setEnabled(value);
    }

    public static boolean isCleanWaterFogEnabled() {
        return Feature.CLEAN_WATER_FOG.isEnabled();
    }

    public static void setCleanWaterFogEnabled(boolean value) {
        Feature.CLEAN_WATER_FOG.setEnabled(value);
    }

    // --------------------------------------------------------------------
    // AFK timeout
    // --------------------------------------------------------------------

    /** Inactivity timeout before AFK Eco engages, in seconds. */
    public static int getAfkTimeoutSeconds() {
        return AFK_TIMEOUT_SECONDS.get();
    }

    /**
     * Sets the inactivity timeout before AFK Eco engages.
     *
     * <p>The value is clamped to [{@value #MIN_AFK_TIMEOUT_SECONDS},
     * {@value #MAX_AFK_TIMEOUT_SECONDS}] seconds so that a hand-edited config file
     * cannot disable the feature (a zero timeout would eco out permanently) or make
     * it useless.</p>
     */
    public static void setAfkTimeoutSeconds(int seconds) {
        AFK_TIMEOUT_SECONDS.set(Math.min(MAX_AFK_TIMEOUT_SECONDS,
                Math.max(MIN_AFK_TIMEOUT_SECONDS, seconds)));
    }

    // --------------------------------------------------------------------
    // Weather mode
    // --------------------------------------------------------------------

    /** The active weather rendering mode. */
    public static WeatherMode getWeatherMode() {
        return WEATHER_MODE.get();
    }

    /** Sets the weather rendering mode; a {@code null} mode is ignored. */
    public static void setWeatherMode(WeatherMode mode) {
        if (mode != null) {
            WEATHER_MODE.set(mode);
        }
    }

    // --------------------------------------------------------------------
    // Test seam
    // --------------------------------------------------------------------

    /**
     * Restores every headline flag to its compiled default.
     *
     * <p>Only used by the unit tests: the shared singletons live for the whole JVM,
     * so a test that flips a flag has to put it back.</p>
     */
    public static void resetToDefaults() {
        for (Feature feature : Feature.values()) {
            feature.setEnabled(true);
        }
        AFK_TIMEOUT_SECONDS.set(DEFAULT_AFK_TIMEOUT_SECONDS);
        WEATHER_MODE.set(WeatherMode.MINIMAL);
    }

    /**
     * Clears the "wired" markers.
     *
     * <p>Test seam only. A unit test that marks a feature as wired has to be able to
     * undo that, otherwise the next test class in the same JVM would see a feature
     * that is not really implemented yet.</p>
     */
    public static void resetWiredForTests() {
        for (Feature feature : Feature.values()) {
            feature.wired = false;
        }
    }
}

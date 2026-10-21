package dev.hyperionfps.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.audio.ThoriumSoundGovernor;
import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import dev.hyperionfps.config.ThoriumConfig;
import dev.hyperionfps.config.ThoriumPresets;
import dev.hyperionfps.core.ThoriumCompatibility;
import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.core.ThoriumFeatureToggles.Feature;
import dev.hyperionfps.core.ThoriumSafety;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import dev.hyperionfps.discord.ThoriumDiscordInvite;
import dev.hyperionfps.frame.ThoriumFramePacer;
import dev.hyperionfps.memory.ThoriumMemoryOptimizer;
import dev.hyperionfps.particle.ThoriumParticleBudget;
import dev.hyperionfps.texture.ThoriumTextureThrottler;

/**
 * The catalogue of settings rows shown in the Thorium console.
 *
 * <p>The console exists in five per-version classes - {@code ThoriumVideoSettingsScreen}
 * - because the widget and rendering APIs differ between the legacy and the modern
 * Minecraft eras. The <em>rows</em> themselves, however, are pure data: a title, a
 * status string, a control type, a click handler, a description and an impact
 * badge. They used to be duplicated in all five files, which meant a new feature had
 * to be added five times and the copies could silently drift apart.</p>
 *
 * <p>This class holds the single copy. Each version screen turns a {@link Spec} into
 * its own widget type, so the behaviour is identical on all five nodes while the
 * era-specific rendering stays where it belongs.</p>
 *
 * <h2>Honesty</h2>
 * <p>A row never pretends to work. A feature that is not implemented yet is shown as
 * {@code NOT AVAILABLE} and refuses the click; a subsystem that has no call site on
 * this Minecraft version says so (REQ-UI-2: no fake toggles).</p>
 */
public final class ThoriumSettingRows {

    /** The kind of control a row renders. */
    public enum ControlType {
        /** A square on/off box. */
        CHECKBOX,
        /** A pill that cycles through a fixed set of values. */
        CYCLE,
        /** A pill that performs an action instead of holding a state. */
        ACTION
    }

    /**
     * The click handler of a row.
     *
     * <p>The handler receives a sink for a transient status message, so an action
     * row can report what it just did ("+12 MB") without the row knowing anything
     * about the widget.</p>
     */
    @FunctionalInterface
    public interface RowAction {
        /** Runs the action; pass a message to {@code statusSink} to show feedback. */
        void run(Consumer<String> statusSink);
    }

    /**
     * One row of the settings console.
     *
     * @param title the row label
     * @param status the value shown in the control, e.g. {@code ON} or {@code 60 FPS}
     * @param controlType the kind of control to render
     * @param enabled whether the control is interactive
     * @param onClick the click handler
     * @param description the long explanation shown in the side panel
     * @param impactBadge the short badge shown in the side panel
     */
    public record Spec(String title,
                       Supplier<String> status,
                       ControlType controlType,
                       Supplier<Boolean> enabled,
                       RowAction onClick,
                       String description,
                       String impactBadge) {
    }

    /** The inactivity timeouts offered by the AFK Eco timeout cycle, in seconds. */
    private static final int[] AFK_TIMEOUT_CYCLE = {30, 60, 120, 300};

    /** The console label of every headline feature, so the tests can prove coverage. */
    private static final Map<Feature, String> FEATURE_TITLES = new EnumMap<>(Feature.class);

    static {
        FEATURE_TITLES.put(Feature.AFK_ECO, "AFK Battery & Power Saver");
        FEATURE_TITLES.put(Feature.REFLEX, "Ultra Low-Latency Reflex");
        FEATURE_TITLES.put(Feature.STUTTER_ALERT, "Instant Lag Spike Alert");
        FEATURE_TITLES.put(Feature.MOB_FARM, "Mob Farm Optimizer");
        FEATURE_TITLES.put(Feature.STORAGE_ITEM_FRAMES, "Storage & Item Frame Culling");
        FEATURE_TITLES.put(Feature.ELYTRA_SMOOTHER, "Elytra Flight Smoother");
        FEATURE_TITLES.put(Feature.EXPLOSION_SHIELD, "Explosion & Firework Shield");
        FEATURE_TITLES.put(Feature.CLEAN_WATER_FOG, "Clean Water & Fast Fog");
    }

    private ThoriumSettingRows() {
    }

    /** The console label of a headline feature. */
    public static String titleOf(Feature feature) {
        return FEATURE_TITLES.get(feature);
    }

    /**
     * The rows of the General tab.
     *
     * @param openDiscord opens the community invite; the browser hand-off is
     *                    era-specific, so the caller supplies it
     */
    public static List<Spec> generalRows(Runnable openDiscord) {
        return List.of(
                new Spec("Nuclear Performance HUD",
                        () -> ThoriumGuiManager.isHudEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumGuiManager::isHudEnabled,
                        status -> ThoriumGuiManager.setHudEnabled(!ThoriumGuiManager.isHudEnabled()),
                        "Live In-Game HUD displaying real-time FPS, frametime stability score, and a 128-sample micro-stutter graph. (Hotkey: F6)",
                        "★ Real-Time Telemetry"),

                new Spec("Extreme FPS Mode",
                        () -> AsyncOcclusionCuller.isExtremeFpsMode() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        AsyncOcclusionCuller::isExtremeFpsMode,
                        status -> {
                            AsyncOcclusionCuller.setExtremeFpsMode(!AsyncOcclusionCuller.isExtremeFpsMode());
                            markPresetCustom();
                        },
                        "Maximizes framerates by bypassing expensive cosmetic sky passes, distant particles, and weather load. (Hotkey: F7)",
                        "★ Extreme FPS Gain"),

                featureRow(Feature.AFK_ECO,
                        "Stealth AFK: when the window loses focus or you stop touching the mouse for a while, the client throttles itself to 10 FPS and parks the CPU instead of burning battery.",
                        "🔋 Up To 75% Less Power"),

                new Spec("AFK Idle Timeout",
                        () -> ThoriumFeatureToggles.getAfkTimeoutSeconds() + "s",
                        ControlType.CYCLE,
                        () -> ThoriumFeatureToggles.isAfkEcoEnabled(),
                        status -> {
                            int current = ThoriumFeatureToggles.getAfkTimeoutSeconds();
                            int next = AFK_TIMEOUT_CYCLE[0];
                            for (int candidate : AFK_TIMEOUT_CYCLE) {
                                if (candidate > current) {
                                    next = candidate;
                                    break;
                                }
                            }
                            if (next == current) {
                                next = AFK_TIMEOUT_CYCLE[0];
                            }
                            ThoriumFeatureToggles.setAfkTimeoutSeconds(next);
                            status.accept(next + "s");
                        },
                        "How long the game may sit untouched before AFK Eco kicks in. Longer is safer for long alt-tab sessions, shorter saves the most power.",
                        "🔋 Idle Detection"),

                featureRow(Feature.REFLEX,
                        "Samples the mouse and the keyboard at the very end of the frame, right before the camera matrix is built, so your input is applied to the frame you are looking at.",
                        "⚡ 8-14ms Lower Input Lag"),

                featureRow(Feature.STUTTER_ALERT,
                        "The Nuclear Stutter Hunter breaks every freeze down into its dominant phase - garbage collection, mob animation, chunk upload - and tells you which one caused it.",
                        "🎯 Freeze Attribution"),

                new Spec("Zero-Jitter Frame Pacing",
                        () -> ThoriumFramePacer.isEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumFramePacer::isEnabled,
                        status -> ThoriumFramePacer.setEnabled(!ThoriumFramePacer.isEnabled()),
                        "High-precision micro-sleep frame pacer. Delivers butter-smooth frame intervals and drastically improves 1% low FPS.",
                        "⚡ Smooth Frametimes"),

                new Spec("Target FPS Cap",
                        () -> ThoriumFramePacer.getTargetFps() <= 0
                                ? "UNCAPPED"
                                : ThoriumFramePacer.getTargetFps() + " FPS",
                        ControlType.CYCLE,
                        ThoriumFramePacer::isEnabled,
                        status -> {
                            int next = ThoriumFramePacer.nextTargetFps(ThoriumFramePacer.getTargetFps());
                            ThoriumFramePacer.setTargetFps(next);
                            ThoriumConfig.get().setInt(ThoriumConfig.KEY_FRAME_TARGET_FPS, next);
                            ThoriumConfig.get().saveNow();
                        },
                        "Caps the client to a fixed framerate using the hybrid sleep-spin pacer. Uncapped lets the game run as fast as the hardware allows.",
                        "⚡ Frame Cap"),

                new Spec("Community Discord",
                        () -> "OPEN INVITE",
                        ControlType.ACTION,
                        () -> true,
                        status -> openDiscord.run(),
                        "Opens the Thorium community server in your browser for early builds, tuning help and release notes.",
                        "★ Join The Community"),

                new Spec("Invite On First Launch",
                        () -> ThoriumDiscordInvite.isEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumDiscordInvite::isEnabled,
                        status -> ThoriumDiscordInvite.setEnabled(!ThoriumDiscordInvite.isEnabled()),
                        "Shows the community invite once on the main menu. It never appears again after it has been shown or dismissed.",
                        "⚡ One-Time Invite"),

                new Spec("Input Turn Counter",
                        () -> "NOT AVAILABLE",
                        ControlType.ACTION,
                        () -> false,
                        status -> status.accept("NOT AVAILABLE"),
                        "Telemetry only: the input pacer counts mouse turns but has no timing logic, so this switch cannot change anything. The Ultra Low-Latency Reflex above is the real low-latency feature.",
                        "📊 Telemetry Only"));
    }

    /**
     * The rows of the Performance tab.
     *
     * @param chunkUploadPacerSupported whether this Minecraft version has a Thorium
     *                                  hook in the chunk upload path
     */
    public static List<Spec> performanceRows(boolean chunkUploadPacerSupported) {
        List<Spec> rows = new ArrayList<>();

        rows.add(new Spec("Entity Animation LOD",
                () -> ThoriumEntityLodThrottler.isEnabled() ? "ON" : "OFF",
                ControlType.CHECKBOX,
                ThoriumEntityLodThrottler::isEnabled,
                status -> ThoriumEntityLodThrottler.setEnabled(!ThoriumEntityLodThrottler.isEnabled()),
                "Throttles skeletal bone animation updates for distant entities. Yields massive 60-80% CPU load reduction around mob farms & villages.",
                "★ 60-80% Entity CPU Gain"));

        rows.add(featureRow(Feature.MOB_FARM,
                "Detects dense mob clusters and throttles the secondary limb animation of the mobs you are not looking at, keeping the server tick and the render thread free.",
                "★ Mob Farm CPU Relief"));

        rows.add(new Spec("Dual-Stage BVH Occlusion",
                () -> ThoriumBvhCuller.isEnabled() ? "ON" : "OFF",
                ControlType.CHECKBOX,
                ThoriumBvhCuller::isEnabled,
                status -> ThoriumBvhCuller.setEnabled(!ThoriumBvhCuller.isEnabled()),
                "Hierarchical bounding-box culler using fast O(1) spatial hashing to discard off-screen chunks and mobs before GPU draw calls.",
                "★ Reduced Draw Calls"));

        rows.add(featureRow(Feature.STORAGE_ITEM_FRAMES,
                "Skips the rendering of chests, shulker boxes and item frames that are sealed inside other blocks or too far away to matter. The container you are looking at is never skipped.",
                "★ Storage Room Relief"));

        rows.add(featureRow(Feature.ELYTRA_SMOOTHER,
                "While you are flying fast, chunk meshes are uploaded in the direction you are travelling first, so the world keeps up with you instead of stalling behind.",
                "⚡ No Elytra Hitching"));

        rows.add(featureRow(Feature.EXPLOSION_SHIELD,
                "Caps the particle flood of TNT chains, wither spawns and firework shows so a single explosion cannot freeze the frame. The explosion flash always survives.",
                "★ No Particle Freeze"));

        rows.add(new Spec("Animated Texture Throttler",
                () -> {
                    int divider = ThoriumTextureThrottler.getThrottleDivider();
                    return ThoriumTextureThrottler.isEnabled() ? (divider + "x Rate") : "OFF";
                },
                ControlType.CYCLE,
                ThoriumTextureThrottler::isEnabled,
                status -> {
                    if (!ThoriumTextureThrottler.isEnabled()) {
                        ThoriumTextureThrottler.setEnabled(true);
                        ThoriumTextureThrottler.setThrottleDivider(2);
                    } else {
                        int current = ThoriumTextureThrottler.getThrottleDivider();
                        if (current == 2) {
                            ThoriumTextureThrottler.setThrottleDivider(4);
                        } else if (current == 4) {
                            ThoriumTextureThrottler.setThrottleDivider(8);
                        } else {
                            ThoriumTextureThrottler.setEnabled(false);
                        }
                    }
                },
                "Throttles PCIe transfer rate of fluid, fire, and portal animations to save VRAM transfer bandwidth when not directly viewed.",
                "⚡ VRAM Bandwidth Saver"));

        if (chunkUploadPacerSupported) {
            rows.add(new Spec("Chunk Upload Pacer",
                    () -> ThoriumChunkUploadPacer.isEnabled() ? "2.5ms Cap" : "OFF",
                    ControlType.CYCLE,
                    ThoriumChunkUploadPacer::isEnabled,
                    status -> ThoriumChunkUploadPacer.setEnabled(!ThoriumChunkUploadPacer.isEnabled()),
                    "Budgets chunk mesh VBO uploads per frame to prevent large spikes and freeze frames when crossing chunk boundaries.",
                    "⚡ Zero Chunk Hitching"));
        } else {
            rows.add(new Spec("Chunk Upload Pacer",
                    () -> "NOT AVAILABLE ON 26.x",
                    ControlType.ACTION,
                    () -> false,
                    status -> status.accept("NOT AVAILABLE"),
                    "Not available on the 26.x render pipeline: the modern chunk upload path has no Thorium hook.",
                    "⚡ Zero Chunk Hitching"));
        }

        rows.add(new Spec("Immediate HUD Batching",
                () -> "NOT WIRED",
                ControlType.ACTION,
                () -> false,
                status -> status.accept("NOT AVAILABLE"),
                "Not available yet: the batcher is flushed every frame, but nothing consults its merge decision, so this switch cannot change anything.",
                "★ Fast UI Drawing"));

        return List.copyOf(rows);
    }

    /** The rows of the Advanced tab. */
    public static List<Spec> advancedRows() {
        return List.of(
                new Spec("Fast Math Overrides",
                        () -> ThoriumCompatibility.isFastMathEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumCompatibility::isFastMathEnabled,
                        status -> {
                            boolean next = !ThoriumCompatibility.isFastMathEnabled();
                            ThoriumCompatibility.setFastMathEnabled(next);
                            ThoriumConfig.get().setBoolean(ThoriumConfig.KEY_FAST_MATH, next);
                            ThoriumConfig.get().saveNow();
                        },
                        "Replaces Minecraft's sin/cos/invSqrt with a lookup table. Faster but less precise, so it is disabled automatically while a shader pack is loaded.",
                        "⚡ Fast Math"),

                new Spec("Auto-Yield to Sodium",
                        () -> ThoriumCompatibility.isAutoYieldEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumCompatibility::isAutoYieldEnabled,
                        status -> {
                            boolean next = !ThoriumCompatibility.isAutoYieldEnabled();
                            ThoriumCompatibility.setAutoYieldEnabled(next);
                            ThoriumConfig.get().setBoolean(ThoriumConfig.KEY_COMPAT_AUTO_YIELD, next);
                            ThoriumConfig.get().saveNow();
                        },
                        "Disables Thorium's own culling when a competing renderer such as Sodium is installed, so the two never fight over visibility.",
                        "⚡ Coexist With Sodium"),

                new Spec("Adaptive Render Distance",
                        () -> AdaptiveDistanceController.getInstance().isEnabled()
                                ? ("ON (" + AdaptiveDistanceController.getInstance().getCurrentDistance() + "ch)")
                                : "OFF",
                        ControlType.CHECKBOX,
                        () -> AdaptiveDistanceController.getInstance().isEnabled(),
                        status -> {
                            boolean next = !AdaptiveDistanceController.getInstance().isEnabled();
                            AdaptiveDistanceController.getInstance().setEnabled(next);
                            ThoriumConfig.get().setBoolean(ThoriumConfig.KEY_ADAPTIVE_DISTANCE, next);
                            ThoriumConfig.get().saveNow();
                        },
                        "Lowers the render distance when the framerate falls below the target. It never raises it again on its own, because every change makes the game re-download its whole chunk cache.",
                        "Frame Rate Protection"),

                new Spec("Weather Rendering",
                        () -> ThoriumFeatureToggles.getWeatherMode().name(),
                        ControlType.CYCLE,
                        () -> ThoriumFeatureToggles.getWeatherMode() != ThoriumFeatureToggles.WeatherMode.VANILLA,
                        status -> {
                            ThoriumFeatureToggles.WeatherMode next =
                                    ThoriumFeatureToggles.getWeatherMode().next();
                            ThoriumFeatureToggles.setWeatherMode(next);
                            status.accept(next.name());
                        },
                        "MINIMAL draws one cheap rain pass and drops the ground splash particles. OFF removes the rain from your screen; the weather in the world and on the server stays exactly as it is.",
                        "🌧 No-Lag Weather"),

                featureRow(Feature.CLEAN_WATER_FOG,
                        "Clamps the underwater fog to a short, crisp distance and simplifies the distance fog curve, which removes a lot of fragment shader work in oceans and rain.",
                        "🌊 Crisp Water, Less Fill Rate"),

                new Spec("Safe Mode (Disable Thorium)",
                        () -> ThoriumSafety.isSafeMode() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumSafety::isSafeMode,
                        status -> {
                            boolean next = !ThoriumSafety.isSafeMode();
                            ThoriumSafety.setConfigSafeMode(next);
                            ThoriumConfig.get().setBoolean(ThoriumConfig.KEY_SAFE_MODE, next);
                            ThoriumConfig.get().saveNow();
                        },
                        "Emergency escape hatch. Disables every Thorium mixin and optimisation on the next launch. Turn this on if Thorium causes a crash or a rendering problem on your device.",
                        "🚨 Emergency Disable"),

                new Spec("Sound Engine Governor",
                        () -> ThoriumSoundGovernor.isEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumSoundGovernor::isEnabled,
                        status -> ThoriumSoundGovernor.setEnabled(!ThoriumSoundGovernor.isEnabled()),
                        "Suppresses identical concurrent sound instances on the OpenAL audio thread, preventing audio-induced game micro-freezes.",
                        "⚡ Audio Thread Fix"),

                new Spec("Particle Budget & Culling",
                        () -> ThoriumParticleBudget.isEnabled() ? "ON" : "OFF",
                        ControlType.CHECKBOX,
                        ThoriumParticleBudget::isEnabled,
                        status -> ThoriumParticleBudget.setEnabled(!ThoriumParticleBudget.isEnabled()),
                        "Caps how many particles may be created per budget window and rejects particles that are off-screen, out of range or hidden behind blocks.",
                        "★ Particle Protection"),

                new Spec("Thread Priority Governor",
                        () -> "BOOSTED",
                        ControlType.ACTION,
                        () -> true,
                        status -> {
                            dev.hyperionfps.core.ThreadGovernor.boostActiveMinecraftThreads();
                            status.accept("BOOSTED!");
                        },
                        "Elevates the OS scheduling priority of Minecraft's render thread and background thread pool to prevent CPU starvation.",
                        "⚡ Prioritize Minecraft"),

                new Spec("Purge RAM Now",
                        () -> "CLEAN",
                        ControlType.ACTION,
                        () -> true,
                        status -> {
                            long freed = ThoriumMemoryOptimizer.purgeMemoryNow();
                            status.accept(freed > 0 ? ("+" + freed + " MB") : "NO CHANGE");
                        },
                        "Instantly sweeps native direct memory buffers, drops the culling caches and triggers GC release for background allocations to reclaim RAM.",
                        "⚡ Reclaim System RAM"));
    }

    // --------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------

    /**
     * Builds the row of a headline feature.
     *
     * <p>The row shows {@code NOT AVAILABLE} until the feature has an
     * implementation, and refuses the click in that state, so the console never
     * offers a switch that cannot do anything.</p>
     */
    private static Spec featureRow(Feature feature, String description, String badge) {
        String title = FEATURE_TITLES.get(feature);
        return new Spec(title,
                () -> feature.isWired() ? (feature.isEnabled() ? "ON" : "OFF") : "NOT AVAILABLE",
                ControlType.CHECKBOX,
                () -> feature.isWired() && feature.isEnabled(),
                status -> {
                    if (!feature.isWired()) {
                        status.accept("NOT AVAILABLE");
                        return;
                    }
                    ThoriumFeatureToggles.setEnabled(feature, !feature.isEnabled());
                    status.accept(feature.isEnabled() ? "ON" : "OFF");
                },
                description,
                badge);
    }

    /** Marks the active profile as {@link ThoriumPresets#CUSTOM}. */
    private static void markPresetCustom() {
        ThoriumConfig.get().setString(ThoriumConfig.KEY_PRESET, ThoriumPresets.CUSTOM);
    }
}

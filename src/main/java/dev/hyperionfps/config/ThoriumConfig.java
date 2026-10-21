package dev.hyperionfps.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hyperionfps.adaptive.AdaptiveDistanceController;
import dev.hyperionfps.anim.ThoriumEntityLodThrottler;
import dev.hyperionfps.audio.ThoriumSoundGovernor;
import dev.hyperionfps.batch.ThoriumImmediateBatcher;
import dev.hyperionfps.chunk.ThoriumChunkUploadPacer;
import dev.hyperionfps.culling.AsyncOcclusionCuller;
import dev.hyperionfps.culling.OcclusionGrid;
import dev.hyperionfps.culling.SectionCullingEngine;
import dev.hyperionfps.culling.ThoriumBvhCuller;
import dev.hyperionfps.core.ThoriumCompatibility;
import dev.hyperionfps.core.ThoriumSafety;
import dev.hyperionfps.frame.ThoriumFramePacer;
import dev.hyperionfps.gui.ThoriumGuiManager;
import dev.hyperionfps.input.ThoriumInputPacer;
import dev.hyperionfps.instancing.EntityInstancer;
import dev.hyperionfps.lighting.AsyncLightEngine;
import dev.hyperionfps.particle.ParticleBatchEngine;
import dev.hyperionfps.particle.ThoriumParticleBudget;
import dev.hyperionfps.render.batching.HudBatcher;
import dev.hyperionfps.texture.ThoriumTextureThrottler;

/**
 * Persistent user-settings store for Thorium.
 *
 * <p>Every optimisation in Thorium is controlled by an in-memory flag. Before this
 * class existed those flags lived only for the lifetime of the JVM, so the hardware
 * presets (Potato / Balanced / Nuclear) reverted to defaults on every restart. This
 * store persists them to a small flat JSON file inside the Fabric config directory.</p>
 *
 * <h2>Design constraints</h2>
 * <ul>
 *   <li><b>No new dependencies.</b> The format is a flat JSON object of string values,
 *       written and parsed by the hand-rolled reader/writer below. Minecraft ships
 *       Gson, but it is not on the compile classpath of every version node, and
 *       pulling it in would change the mod's dependency footprint.</li>
 *   <li><b>Never throws.</b> A broken or unwritable config file must degrade to
 *       compiled defaults, never prevent the game from starting.</li>
 *   <li><b>Atomic writes.</b> The file is written to a sibling {@code .tmp} and then
 *       moved, so a crash mid-write cannot leave a truncated config behind.</li>
 *   <li><b>Forward compatible.</b> Unknown keys are preserved on load and rewritten on
 *       save, so downgrading and re-upgrading does not silently drop settings.</li>
 * </ul>
 */
public final class ThoriumConfig {

    /** File name inside the Fabric config directory. */
    public static final String FILE_NAME = "thorium-config.json";

    // --------------------------------------------------------------------
    // Keys
    // --------------------------------------------------------------------

    /** Name of the active hardware preset ({@code POTATO}/{@code BALANCED}/{@code NUCLEAR}/{@code CUSTOM}). */
    public static final String KEY_PRESET = "preset";

    public static final String KEY_HUD = "hud.enabled";
    public static final String KEY_EXTREME_FPS = "culling.extremeFps";
    public static final String KEY_CULLING = "culling.enabled";
    public static final String KEY_SECTION_CULLING = "culling.section.enabled";
    public static final String KEY_OCCLUSION_GRID = "culling.occlusionGrid.enabled";
    public static final String KEY_BVH = "culling.bvh.enabled";
    public static final String KEY_ENTITY_LOD = "anim.entityLod.enabled";
    public static final String KEY_FRAME_PACER = "frame.pacer.enabled";

    /** Target framerate cap for the frame pacer; {@code 0} means uncapped. */
    public static final String KEY_FRAME_TARGET_FPS = "frame.pacer.targetFps";
    public static final String KEY_INPUT_PACER = "input.pacer.enabled";
    public static final String KEY_CHUNK_PACER = "chunk.uploader.enabled";
    public static final String KEY_TEXTURE_THROTTLE = "texture.throttler.enabled";
    public static final String KEY_TEXTURE_DIVIDER = "texture.throttler.divider";
    public static final String KEY_IMMEDIATE_BATCHER = "batch.immediate.enabled";
    public static final String KEY_HUD_BATCHER = "batch.hud.enabled";
    public static final String KEY_SOUND_GOVERNOR = "audio.governor.enabled";
    public static final String KEY_PARTICLE_BUDGET = "particle.budget.enabled";
    public static final String KEY_PARTICLE_BATCHER = "particle.batcher.enabled";
    public static final String KEY_ENTITY_INSTANCER = "entity.instancer.enabled";
    public static final String KEY_LIGHT_ENGINE = "lighting.async.enabled";
    public static final String KEY_ADAPTIVE_DISTANCE = "adaptive.distance.enabled";

    /** Persisted safe-mode switch - disables every Thorium optimisation. */
    public static final String KEY_SAFE_MODE = "safeMode";

    /** Whether Thorium stands down for a competing renderer (Sodium family). */
    public static final String KEY_COMPAT_AUTO_YIELD = "compat.autoYield";

    /** Whether the fast-math overrides of {@code Mth} may run. */
    public static final String KEY_FAST_MATH = "math.fast.enabled";

    /** Whether the one-time community invite may be shown on the first launch. */
    public static final String KEY_DISCORD_INVITE_ENABLED = "discord.invite.enabled";

    /** Whether the one-time community invite has already been offered. */
    public static final String KEY_DISCORD_INVITE_SHOWN = "discord.invite.shown";

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/Config");

    private static volatile ThoriumConfig instance;

    private final Map<String, String> values = new LinkedHashMap<>();
    private Path file;
    private boolean loaded;

    private ThoriumConfig(Path file) {
        this.file = file;
    }

    // --------------------------------------------------------------------
    // Lifecycle
    // --------------------------------------------------------------------

    /**
     * Process-wide configuration instance, loading the backing file on first use.
     *
     * <p>Never throws: if the file cannot be located or read, an empty in-memory
     * configuration is returned and compiled defaults are used.</p>
     */
    public static ThoriumConfig get() {
        ThoriumConfig local = instance;
        if (local == null) {
            synchronized (ThoriumConfig.class) {
                local = instance;
                if (local == null) {
                    local = new ThoriumConfig(defaultFile());
                    local.load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Test seam: a configuration bound to an explicit file. */
    static ThoriumConfig forFile(Path file) {
        ThoriumConfig config = new ThoriumConfig(file);
        config.load();
        return config;
    }

    private static Path defaultFile() {
        try {
            Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            return dir.resolve(FILE_NAME);
        } catch (Throwable unavailable) {
            // Outside a Fabric loader (unit tests, tooling) there is nowhere to persist.
            return null;
        }
    }

    // --------------------------------------------------------------------
    // Typed accessors
    // --------------------------------------------------------------------

    /** Reads a boolean, falling back to {@code fallback} when absent or unparsable. */
    public synchronized boolean getBoolean(String key, boolean fallback) {
        String raw = values.get(key);
        if (raw == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return fallback;
    }

    /** Writes a boolean. Does not persist to disk - call {@link #save()}. */
    public synchronized void setBoolean(String key, boolean value) {
        values.put(key, Boolean.toString(value));
    }

    /** Reads an int, falling back to {@code fallback} when absent or unparsable. */
    public synchronized int getInt(String key, int fallback) {
        String raw = values.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    /** Writes an int. Does not persist to disk - call {@link #save()}. */
    public synchronized void setInt(String key, int value) {
        values.put(key, Integer.toString(value));
    }

    /** Reads a string, falling back to {@code fallback} when absent. */
    public synchronized String getString(String key, String fallback) {
        String raw = values.get(key);
        return raw == null ? fallback : raw;
    }

    /** Writes a string. Does not persist to disk - call {@link #save()}. */
    public synchronized void setString(String key, String value) {
        values.put(key, value == null ? "" : value);
    }

    /** Immutable view of the raw key/value pairs. */
    public synchronized Map<String, String> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    // --------------------------------------------------------------------
    // Disk I/O
    // --------------------------------------------------------------------

    /** Reads the backing file, if present. Never throws. */
    public synchronized void load() {
        this.loaded = true;
        if (file == null) {
            return;
        }
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            values.putAll(parseFlatJson(text));
            // Safe mode is read here so that every later subsystem check sees it.
            ThoriumSafety.setConfigSafeMode(getBoolean(KEY_SAFE_MODE, false));
            LOGGER.info("Loaded {} setting(s) from {}", values.size(), file);
        } catch (Throwable failure) {
            LOGGER.warn("Could not read Thorium config from {} ({}); using defaults",
                    file, failure.toString());
        }
    }

    /**
     * Writes the current values to disk atomically.
     *
     * <p>Never throws. A failure is logged and the in-memory values are kept, so the
     * next save attempt can still succeed.</p>
     */
    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, writeFlatJson(values), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable failure) {
            LOGGER.warn("Could not save Thorium config to {} ({})", file, failure.toString());
        }
    }

    /** True when this instance is backed by a writable file. */
    public synchronized boolean isPersistent() {
        return file != null;
    }

    /** Path of the backing file, or {@code null} when persistence is unavailable. */
    public synchronized Path file() {
        return file;
    }

    // --------------------------------------------------------------------
    // Subsystem bridge
    // --------------------------------------------------------------------

    /**
     * Pushes the persisted values into the live subsystems.
     *
     * <p>Called once during client initialisation, before the first frame. Every
     * subsystem call is individually guarded so that one misbehaving subsystem cannot
     * stop the others from being restored.</p>
     */
    public void applyToSubsystems() {
        if (!loaded) {
            load();
        }
        if (ThoriumSafety.isSafeMode()) {
            LOGGER.warn("Safe mode is active - persisted settings were not applied.");
            return;
        }
        run("HUD", () -> ThoriumGuiManager.setHudEnabled(getBoolean(KEY_HUD, true)));
        run("Extreme FPS mode", () -> AsyncOcclusionCuller.setExtremeFpsMode(getBoolean(KEY_EXTREME_FPS, false)));
        run("culling", () -> AsyncOcclusionCuller.setEnabled(getBoolean(KEY_CULLING, true)));
        run("section culling", () -> SectionCullingEngine.setEnabled(getBoolean(KEY_SECTION_CULLING, true)));
        run("occlusion grid", () -> OcclusionGrid.setEnabled(getBoolean(KEY_OCCLUSION_GRID, true)));
        run("BVH culler", () -> ThoriumBvhCuller.setEnabled(getBoolean(KEY_BVH, true)));
        run("entity LOD", () -> ThoriumEntityLodThrottler.setEnabled(getBoolean(KEY_ENTITY_LOD, true)));
        run("frame pacer", () -> ThoriumFramePacer.setEnabled(getBoolean(KEY_FRAME_PACER, true)));
        run("frame target fps", () -> ThoriumFramePacer.setTargetFps(getInt(KEY_FRAME_TARGET_FPS, 0)));
        run("input pacer", () -> ThoriumInputPacer.setEnabled(getBoolean(KEY_INPUT_PACER, true)));
        run("chunk upload pacer", () -> ThoriumChunkUploadPacer.setEnabled(getBoolean(KEY_CHUNK_PACER, true)));
        run("texture throttler", () -> ThoriumTextureThrottler.setEnabled(getBoolean(KEY_TEXTURE_THROTTLE, true)));
        run("texture divider", () -> ThoriumTextureThrottler.setThrottleDivider(getInt(KEY_TEXTURE_DIVIDER, 2)));
        run("immediate batcher", () -> ThoriumImmediateBatcher.setEnabled(getBoolean(KEY_IMMEDIATE_BATCHER, true)));
        run("HUD batcher", () -> HudBatcher.setEnabled(getBoolean(KEY_HUD_BATCHER, true)));
        run("sound governor", () -> ThoriumSoundGovernor.setEnabled(getBoolean(KEY_SOUND_GOVERNOR, true)));
        run("particle budget", () -> ThoriumParticleBudget.setEnabled(getBoolean(KEY_PARTICLE_BUDGET, true)));
        run("particle batcher", () -> ParticleBatchEngine.getInstance().setEnabled(getBoolean(KEY_PARTICLE_BATCHER, true)));
        run("entity instancer", () -> EntityInstancer.getInstance().setEnabled(getBoolean(KEY_ENTITY_INSTANCER, true)));
        run("light engine", () -> AsyncLightEngine.getInstance().setEnabled(getBoolean(KEY_LIGHT_ENGINE, true)));
        // Opt-in: the governor rewrites a vanilla option, and in single-player that
        // option is mirrored by the integrated server into the player list's view
        // distance, which makes the client drop and re-download its whole chunk cache.
        // It therefore has to be something the player turns on knowingly.
        run("adaptive distance", () -> AdaptiveDistanceController.getInstance().setEnabled(getBoolean(KEY_ADAPTIVE_DISTANCE, false)));
        run("fast math", () -> ThoriumCompatibility.setFastMathEnabled(getBoolean(KEY_FAST_MATH, false)));

        LOGGER.info("Applied persisted Thorium settings (preset={}, hud={}, extremeFps={})",
                getString(KEY_PRESET, "BALANCED"),
                getBoolean(KEY_HUD, true),
                getBoolean(KEY_EXTREME_FPS, false));
    }

    /**
     * Reads the live subsystem states back into the store.
     *
     * <p>Called immediately before a save so that toggles changed outside the settings
     * screen (hotkeys, preset buttons) are captured too.</p>
     */
    public synchronized void captureFromSubsystems() {
        setBoolean(KEY_SAFE_MODE, ThoriumSafety.isSafeMode());
        setBoolean(KEY_HUD, ThoriumGuiManager.isHudEnabled());
        setBoolean(KEY_EXTREME_FPS, AsyncOcclusionCuller.isExtremeFpsMode());
        setBoolean(KEY_CULLING, AsyncOcclusionCuller.isEnabled());
        setBoolean(KEY_SECTION_CULLING, SectionCullingEngine.isEnabled());
        setBoolean(KEY_OCCLUSION_GRID, OcclusionGrid.isEnabled());
        setBoolean(KEY_BVH, ThoriumBvhCuller.isEnabled());
        setBoolean(KEY_ENTITY_LOD, ThoriumEntityLodThrottler.isEnabled());
        setBoolean(KEY_FRAME_PACER, ThoriumFramePacer.isEnabled());
        setInt(KEY_FRAME_TARGET_FPS, ThoriumFramePacer.getTargetFps());
        setBoolean(KEY_INPUT_PACER, ThoriumInputPacer.isEnabled());
        setBoolean(KEY_CHUNK_PACER, ThoriumChunkUploadPacer.isEnabled());
        setBoolean(KEY_TEXTURE_THROTTLE, ThoriumTextureThrottler.isEnabled());
        setInt(KEY_TEXTURE_DIVIDER, ThoriumTextureThrottler.getThrottleDivider());
        setBoolean(KEY_IMMEDIATE_BATCHER, ThoriumImmediateBatcher.isEnabled());
        setBoolean(KEY_HUD_BATCHER, HudBatcher.isEnabled());
        setBoolean(KEY_SOUND_GOVERNOR, ThoriumSoundGovernor.isEnabled());
        setBoolean(KEY_PARTICLE_BUDGET, ThoriumParticleBudget.isEnabled());
        setBoolean(KEY_PARTICLE_BATCHER, ParticleBatchEngine.getInstance().isEnabled());
        setBoolean(KEY_ENTITY_INSTANCER, EntityInstancer.getInstance().isEnabled());
        setBoolean(KEY_LIGHT_ENGINE, AsyncLightEngine.getInstance().isEnabled());
        setBoolean(KEY_ADAPTIVE_DISTANCE, AdaptiveDistanceController.getInstance().isEnabled());
        setBoolean(KEY_FAST_MATH, ThoriumCompatibility.isFastMathEnabled());
    }

    /**
     * Captures the live state and persists it.
     *
     * <p>This is the single entry point the UI calls after any user action.</p>
     */
    public void saveNow() {
        captureFromSubsystems();
        save();
    }

    private void run(String what, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            LOGGER.warn("Could not restore setting '{}' ({}); keeping default", what, failure.toString());
        }
    }

    // --------------------------------------------------------------------
    // Minimal flat-JSON reader / writer
    // --------------------------------------------------------------------

    /**
     * Parses a flat JSON object of string values.
     *
     * <p>Malformed input never throws: parsing stops at the first structural problem
     * and whatever was read up to that point is returned.</p>
     */
    static Map<String, String> parseFlatJson(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        int[] cursor = {0};
        skipWhitespace(text, cursor);
        if (cursor[0] >= text.length() || text.charAt(cursor[0]) != '{') {
            return out;
        }
        cursor[0]++;

        while (cursor[0] < text.length()) {
            skipWhitespace(text, cursor);
            if (cursor[0] >= text.length()) {
                break;
            }
            char c = text.charAt(cursor[0]);
            if (c == '}') {
                break;
            }
            if (c == ',') {
                cursor[0]++;
                continue;
            }
            if (c != '"') {
                // Unexpected token - skip it and keep looking for a key.
                cursor[0]++;
                continue;
            }
            String key = readJsonString(text, cursor);
            if (key == null) {
                break;
            }
            skipWhitespace(text, cursor);
            if (cursor[0] < text.length() && text.charAt(cursor[0]) == ':') {
                cursor[0]++;
                skipWhitespace(text, cursor);
                String value = readJsonString(text, cursor);
                if (value == null) {
                    break;
                }
                out.put(key, value);
            } else {
                out.put(key, "");
            }
        }
        return out;
    }

    /** Serialises a flat map into a readable JSON object. */
    static String writeFlatJson(Map<String, String> map) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            out.append("  \"").append(escapeJson(entry.getKey()))
                    .append("\": \"").append(escapeJson(entry.getValue()))
                   .append("\"");
        }
        out.append("\n}\n");
        return out.toString();
    }

    private static void skipWhitespace(String text, int[] cursor) {
        while (cursor[0] < text.length() && Character.isWhitespace(text.charAt(cursor[0]))) {
            cursor[0]++;
        }
    }

    private static String readJsonString(String text, int[] cursor) {
        if (cursor[0] >= text.length() || text.charAt(cursor[0]) != '"') {
            return null;
        }
        cursor[0]++;
        StringBuilder value = new StringBuilder();
        while (cursor[0] < text.length()) {
            char c = text.charAt(cursor[0]++);
            if (c == '"') {
                return value.toString();
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (cursor[0] >= text.length()) {
                return null;
            }
            char escaped = text.charAt(cursor[0]++);
            switch (escaped) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (cursor[0] + 4 > text.length()) {
                        return null;
                    }
                    String hex = text.substring(cursor[0], cursor[0] + 4);
                    try {
                        value.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException invalid) {
                        value.append('?');
                    }
                    cursor[0] += 4;
                }
                default -> value.append(escaped);
            }
        }
        return null;
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}

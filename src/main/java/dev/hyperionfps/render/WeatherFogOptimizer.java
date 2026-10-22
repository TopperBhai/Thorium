package dev.hyperionfps.render;

import java.util.concurrent.atomic.AtomicLong;

import dev.hyperionfps.core.ThoriumFeatureToggles;
import dev.hyperionfps.core.ThoriumFeatureToggles.WeatherMode;

/**
 * No-Lag Weather &amp; Clean Water / Fast Fog (REQ-HEAD-8, REQ-HEAD-9).
 *
 * <p>Rain is the one weather effect that Minecraft spends real work on. In heavy rain
 * the client spawns a <em>splash particle</em> on every ground block around the player:
 * on the modern versions that is {@code 0.225 * (2r+1)^2 * rainLevel^2} particles
 * <em>per tick</em> - well over a hundred particles per tick and several thousand per
 * second, each one a real particle with its own quad, its own lifetime and its own
 * tick. That flood is what makes a rainstorm expensive, and it is entirely
 * cosmetic: the player cannot tell one splash from the next.</p>
 *
 * <h2>The three weather modes</h2>
 * <ul>
 *   <li>{@link WeatherMode#VANILLA} - nothing is touched. The rain sheets and the
 *       splash particles are drawn exactly as vanilla draws them.</li>
 *   <li>{@link WeatherMode#MINIMAL} - the rain sheets are drawn exactly as vanilla
 *       draws them, but the <em>splash particles are gone</em>. The rain is still
 *       there, the world still rains, the rain sound still plays - only the flood of
 *       ground splashes is removed.</li>
 *   <li>{@link WeatherMode#OFF} - the precipitation layer is not drawn at all. The
 *       world's weather state, the server's weather state and the rain sound are all
 *       left exactly as they are; only the visual layer on the client's screen is
 *       gone.</li>
 * </ul>
 *
 * <h2>Clean water &amp; fast fog</h2>
 * <p>The fog is a visual decision, not a work decision. Vanilla fades the terrain into
 * the fog colour over a band that grows with the render distance
 * ({@code clamp(viewDistance/10, 4, 64)} blocks), and underwater it fades into the
 * water colour over 96 blocks. This engine does two things to it:</p>
 * <ul>
 *   <li><b>Clean water</b> - the underwater fog is clamped to a short, crisp distance,
 *       so the water reads as a clean tint instead of a long murky gradient.</li>
 *   <li><b>Fast fog</b> - the distance fog band is narrowed, so the horizon resolves
 *       into the fog colour over a short, sharp transition instead of a wide,
 *       gradual one.</li>
 * </ul>
 *
 * <h2>Honesty about the benefit</h2>
 * <p>The fog part of this feature is <b>visual</b>. The Minecraft fog shader computes
 * its blend for every fragment whether the fog band is 24 blocks wide or 64, so
 * narrowing the band does <em>not</em> measurably reduce fragment work. A first
 * draft of this feature's console text claimed exactly that; the claim was wrong,
 * and the text now says so. The measurable work reduction in this step comes from
 * the weather side: the splash particle flood is real work that is really removed,
 * and the counters below count it.</p>
 *
 * <h2>Purity</h2>
 * <p>This class contains no Minecraft type at all. It is a pure policy engine, so the
 * whole decision table is unit-testable without a running game. The Minecraft half -
 * cancelling the precipitation pass, dropping the splash particle and rewriting the
 * fog values - lives in the per-era mixins, which are one-line adapters.</p>
 */
public final class WeatherFogOptimizer {

    /**
     * The longest underwater fog the client may show, in blocks.
     *
     * <p>Vanilla fades the water in over 96 blocks (less with the water-vision
     * potion, less again in the biomes that carry the "closer water fog" tag).
     * Clamping the end to this value turns the long gradient into a short, even
     * tint.</p>
     */
    public static final float WATER_FOG_END_MAX = 48.0f;

    /**
     * The widest distance fog band the client may show, in blocks.
     *
     * <p>Vanilla spreads the transition over {@code clamp(viewDistance/10, 4, 64)}
     * blocks, which is 19 blocks at the default render distance and 51 blocks at 32
     * chunks. Narrowing it never widens the band, so at the default settings nothing
     * changes at all - the feature only kicks in on a long render distance.</p>
     */
    public static final float DISTANCE_FOG_BAND_MAX = 24.0f;

    /** Splash particles the weather modes dropped. */
    private static final AtomicLong SPLASH_SUPPRESSED = new AtomicLong();

    /** Precipitation passes that were not drawn. */
    private static final AtomicLong PRECIPITATION_SKIPPED = new AtomicLong();

    /** Fog ranges that were rewritten. */
    private static final AtomicLong FOG_CLAMPS = new AtomicLong();

    private WeatherFogOptimizer() {
    }

    /**
     * A fog range, in blocks.
     *
     * @param start the distance at which the fog begins
     * @param end   the distance at which the fog is complete
     */
    public record FogRange(float start, float end) {
    }

    // --------------------------------------------------------------------
    // The weather decision
    // --------------------------------------------------------------------

    /** The weather rendering mode the player has selected. */
    public static WeatherMode mode() {
        return ThoriumFeatureToggles.getWeatherMode();
    }

    /**
     * Whether the precipitation layer may be drawn.
     *
     * <p>{@code false} only in {@link WeatherMode#OFF}. The world's weather, the
     * server's weather and the rain sound are never touched by this decision - only
     * the visual layer on the screen.</p>
     */
    public static boolean shouldRenderPrecipitation() {
        return mode() != WeatherMode.OFF;
    }

    /**
     * Whether the ground splash particles may be created.
     *
     * <p>{@code false} in {@link WeatherMode#MINIMAL} and {@link WeatherMode#OFF}:
     * both modes drop the flood, because the flood is the expensive part. In
     * {@link WeatherMode#VANILLA} the particles are created exactly as vanilla
     * creates them.</p>
     */
    public static boolean shouldRenderSplashParticles() {
        return mode() == WeatherMode.VANILLA;
    }

    // --------------------------------------------------------------------
    // The fog decision
    // --------------------------------------------------------------------

    /**
     * Clamps the underwater fog to a short, crisp distance.
     *
     * <p>Two things happen:</p>
     * <ul>
     *   <li>the fog no longer starts <em>behind</em> the camera - vanilla starts it
     *       eight blocks back, which fogs the water right in front of the player;</li>
     *   <li>the end distance is capped at {@value #WATER_FOG_END_MAX} blocks, so the
     *       water becomes a short, even tint instead of a 96 block murky
     *       gradient.</li>
     * </ul>
     *
     * <p>A range that is already shorter is left alone, and so is a range that is not
     * usable at all (a NaN, an infinite value, or an end that lies before the
     * start).</p>
     *
     * @param start the vanilla fog start distance
     * @param end   the vanilla fog end distance
     * @return the fog range the client should use
     */
    public static FogRange clampUnderwaterFog(float start, float end) {
        if (!ThoriumFeatureToggles.isCleanWaterFogEnabled()) {
            return new FogRange(start, end);
        }
        if (!isUsable(start) || !isUsable(end) || end <= start) {
            return new FogRange(start, end);
        }
        float clampedStart = Math.max(start, 0.0f);
        float clampedEnd = Math.min(end, WATER_FOG_END_MAX);
        if (clampedStart >= clampedEnd) {
            // The whole range is already inside the cap; clamping would leave nothing
            // to fade across, and the shader would read an empty range as "always
            // fogged".
            return new FogRange(start, end);
        }
        return new FogRange(clampedStart, clampedEnd);
    }

    /**
     * Narrows the distance fog band to a short, sharp transition.
     *
     * <p>The band is the distance between the fog start and the fog end. A band that
     * is already narrow enough is left alone, and so is a range that is not usable
     * at all. The band is never widened.</p>
     *
     * @param start the vanilla fog start distance
     * @param end   the vanilla fog end distance
     * @return the fog range the client should use
     */
    public static FogRange clampDistanceFog(float start, float end) {
        if (!ThoriumFeatureToggles.isCleanWaterFogEnabled()) {
            return new FogRange(start, end);
        }
        if (!isUsable(start) || !isUsable(end) || end <= start) {
            return new FogRange(start, end);
        }
        float band = end - start;
        if (band <= DISTANCE_FOG_BAND_MAX) {
            return new FogRange(start, end);
        }
        if (end <= DISTANCE_FOG_BAND_MAX) {
            // The whole range is shorter than the band; narrowing it would leave
            // nothing to fade across.
            return new FogRange(start, end);
        }
        return new FogRange(end - DISTANCE_FOG_BAND_MAX, end);
    }

    /**
     * Narrows a vanilla fog band to the configured maximum.
     *
     * <p>This is the arithmetic behind {@link #clampDistanceFog}, exposed separately
     * for the versions whose fog is computed inside the game: the modern renderer
     * derives the band as {@code clamp(viewDistance/10, 4, 64)} before it is applied,
     * and a hook that sits on that clamp can narrow the band directly.</p>
     *
     * @param band the vanilla band width in blocks
     * @return the band width the client should use
     */
    public static float clampDistanceFogBand(float band) {
        if (!ThoriumFeatureToggles.isCleanWaterFogEnabled()) {
            return band;
        }
        if (!isUsable(band) || band <= DISTANCE_FOG_BAND_MAX) {
            return band;
        }
        return DISTANCE_FOG_BAND_MAX;
    }

    /** Whether a fog value can be used at all. */
    private static boolean isUsable(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    // --------------------------------------------------------------------
    // Lifecycle and metrics
    // --------------------------------------------------------------------

    /**
     * Announces that the weather and fog optimiser is live in this session.
     *
     * <p>Called once during client initialisation. It flips the feature from "not
     * available" to a working switch in the settings console - the console must
     * never offer a switch that cannot do anything (REQ-UI-2).</p>
     */
    public static void install() {
        ThoriumFeatureToggles.Feature.CLEAN_WATER_FOG.markWired();
    }

    /** Books a splash particle that was dropped. */
    public static void recordSplashSuppressed() {
        SPLASH_SUPPRESSED.incrementAndGet();
    }

    /** Books a precipitation pass that was not drawn. */
    public static void recordPrecipitationSkipped() {
        PRECIPITATION_SKIPPED.incrementAndGet();
    }

    /** Books a fog range that was rewritten. */
    public static void recordFogClamp() {
        FOG_CLAMPS.incrementAndGet();
    }

    /** Splash particles the weather modes dropped. */
    public static long getSplashParticlesSuppressed() {
        return SPLASH_SUPPRESSED.get();
    }

    /** Precipitation passes that were not drawn. */
    public static long getPrecipitationPassesSkipped() {
        return PRECIPITATION_SKIPPED.get();
    }

    /** Fog ranges that were rewritten. */
    public static long getFogClampsApplied() {
        return FOG_CLAMPS.get();
    }

    /**
     * A one-line description of what the optimiser has done so far.
     *
     * <p>Used by the startup subsystem report so the log shows the real state of the
     * feature instead of a static claim (REQ-DIAG-1).</p>
     */
    public static String describeActivity() {
        return mode() + " weather, "
                + SPLASH_SUPPRESSED.get() + " splash particles dropped, "
                + PRECIPITATION_SKIPPED.get() + " precipitation passes skipped, "
                + FOG_CLAMPS.get() + " fog ranges clamped";
    }

    /**
     * A one-line description of what the fog part has done so far.
     *
     * <p>Separate from {@link #describeActivity()} because the fog is a different
     * feature with a different switch: it is the "Clean Water &amp; Fast Fog" row in
     * the console, and the log should say what that row actually did.</p>
     */
    public static String describeFogActivity() {
        return FOG_CLAMPS.get() + " fog ranges clamped, "
                + (ThoriumFeatureToggles.isCleanWaterFogEnabled() ? "enabled" : "disabled by the player");
    }

    /**
     * Puts the counters back to zero.
     *
     * <p>Test seam only: the shared singletons live for the whole JVM, so a test that
     * books work has to be able to undo it.</p>
     */
    public static void resetForTests() {
        SPLASH_SUPPRESSED.set(0L);
        PRECIPITATION_SKIPPED.set(0L);
        FOG_CLAMPS.set(0L);
    }
}

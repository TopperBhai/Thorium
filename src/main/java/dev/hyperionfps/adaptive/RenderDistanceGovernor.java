package dev.hyperionfps.adaptive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Connects the adaptive render-distance governor to the game's own render-distance
 * option.
 *
 * <p>Everything in this class is version-neutral: {@code Options#renderDistance()}
 * returns an {@code OptionInstance<Integer>} with the same shape on every supported
 * Minecraft version, so no per-era mixin is required.</p>
 *
 * <p>The governor must never fight the player. If the render distance is changed by
 * hand in the vanilla video settings, the governor yields for
 * {@link #USER_OVERRIDE_MILLIS} and re-syncs its own state with the new value.</p>
 */
public final class RenderDistanceGovernor {

    /** How long the governor yields after the player changed the render distance. */
    public static final long USER_OVERRIDE_MILLIS = 60_000L;

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/RenderDistance");

    private static volatile int lastKnownDistance = -1;
    private static volatile long userOverrideUntilMillis;

    private RenderDistanceGovernor() {
    }

    /** Registers the governor's consumer. Called once during client initialisation. */
    public static void install() {
        AdaptiveDistanceController controller = AdaptiveDistanceController.getInstance();
        controller.setDistanceConsumer(RenderDistanceGovernor::apply);
        int current = currentDistance();
        if (current > 0) {
            lastKnownDistance = current;
            controller.setCurrentDistance(current);
        }
    }

    /** Applies a new render distance to the game options. */
    private static void apply(int distance) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.options == null || client.level == null) {
                return;
            }
            client.options.renderDistance().set(distance);
            client.options.setServerRenderDistance(client.options.getEffectiveRenderDistance());
            lastKnownDistance = distance;
            LOGGER.info("Adaptive render distance applied: {} chunks", distance);
        } catch (Throwable failure) {
            LOGGER.warn("Could not apply adaptive render distance: {}", failure.toString());
        }
    }

    /**
     * Detects a render distance the player changed by hand and yields to them.
     *
     * <p>Called once per world frame; it performs one integer comparison and never
     * touches the game state unless the distance actually moved.</p>
     */
    public static void pollUserOverride() {
        int current = currentDistance();
        if (current <= 0 || current == lastKnownDistance) {
            return;
        }
        lastKnownDistance = current;
        userOverrideUntilMillis = System.currentTimeMillis() + USER_OVERRIDE_MILLIS;
        AdaptiveDistanceController.getInstance().setCurrentDistance(current);
        LOGGER.info("Render distance changed outside Thorium ({} chunks) - adaptive governor yields for {}s",
                current, USER_OVERRIDE_MILLIS / 1000L);
    }

    /** Whether the governor is currently yielding to the player. */
    public static boolean isUserOverrideActive() {
        return System.currentTimeMillis() < userOverrideUntilMillis;
    }

    /** The render distance currently configured in the game options. */
    public static int currentDistance() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.options == null) {
                return -1;
            }
            Options options = client.options;
            return options.renderDistance().get();
        } catch (Throwable unavailable) {
            return -1;
        }
    }
}

package dev.hyperionfps.core;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global safety switch for Thorium.
 *
 * <p>Thorium hooks deep into the render pipeline. On unusual platforms - software
 * rasterisers, Android launchers that translate OpenGL (Pojav/RxLauncher + LTW),
 * headless CI, or freshly released driver stacks - some of those hooks can interact
 * badly with the host's native graphics layer. When that happens the player needs a
 * way to get the game running again <b>without</b> uninstalling the mod.</p>
 *
 * <h2>Safe mode</h2>
 * <p>Safe mode disables every Thorium mixin and every subsystem. It is enabled by
 * either of:</p>
 * <ul>
 *   <li>The system property {@code -Dhyperionfps.safeMode=true}. This is honoured
 *       from the very first line of mixin bootstrap, before any Minecraft class is
 *       touched, so it is the reliable escape hatch for a broken launch.</li>
 *   <li>The {@code safeMode} key in the persisted Thorium config file. This is read
 *       during client initialisation, i.e. before the window is created, so it also
 *       prevents the frame-pacing hook from running.</li>
 * </ul>
 *
 * <p>The two sources are independent and additive: enabling either one is enough.
 * The property is intentionally checked before the config file so that a player whose
 * game cannot start at all can still recover.</p>
 */
public final class ThoriumSafety {

    /** System property that enables safe mode. */
    public static final String SAFE_MODE_PROPERTY = "hyperionfps.safeMode";

    private static final Logger LOGGER = LoggerFactory.getLogger("Thorium/Safety");

    private static final AtomicBoolean PROPERTY_SAFE_MODE = new AtomicBoolean();
    private static final AtomicBoolean CONFIG_SAFE_MODE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private ThoriumSafety() {
    }

    /**
     * Whether safe mode is currently active.
     *
     * <p>True when either the system property or the persisted config requests it.</p>
     */
    public static boolean isSafeMode() {
        return PROPERTY_SAFE_MODE.get() || CONFIG_SAFE_MODE.get();
    }

    /**
     * Records the system property state.
     *
     * <p>Called during mixin bootstrap, where the JVM system properties are already
     * available but the mod's own subsystems are not yet constructed.</p>
     */
    public static void refreshPropertySafeMode() {
        boolean enabled = Boolean.getBoolean(SAFE_MODE_PROPERTY);
        if (PROPERTY_SAFE_MODE.compareAndSet(!enabled, enabled)) {
            logOnce(enabled);
        }
    }

    /**
     * Records the persisted config state.
     *
     * <p>Called by the config loader once the file has been read.</p>
     */
    public static void setConfigSafeMode(boolean enabled) {
        if (CONFIG_SAFE_MODE.compareAndSet(!enabled, enabled)) {
            logOnce(enabled);
        }
    }

    /** Records the system property state and returns the resulting safe-mode flag. */
    public static boolean refreshPropertySafeModeAndCheck() {
        refreshPropertySafeMode();
        return isSafeMode();
    }

    private static void logOnce(boolean enabled) {
        if (!LOGGED.compareAndSet(false, true)) {
            return;
        }
        if (enabled) {
            LOGGER.warn("Thorium SAFE MODE is ON - every mixin and optimisation is disabled.");
            LOGGER.warn("Thorium is not the cause of any crash while safe mode is active.");
        }
    }
}

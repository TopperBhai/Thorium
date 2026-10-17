package dev.hyperionfps.frame;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frame-pacing policy for the boot window.
 *
 * <p>The game re-applies the player's stored VSync setting every time a window
 * or surface is (re)configured - including during startup, where a stale
 * "VSync on" flag would cap the client at the display refresh rate before the
 * player ever touches the options screen. HyperionFPS therefore overrides swap
 * control only while the JVM is still young (see {@link #BOOT_WINDOW_MILLIS});
 * once that window closes, every later reconfiguration is by definition
 * player-driven and is left untouched, so the in-game VSync toggle keeps
 * working exactly as the player expects.</p>
 *
 * <p>This class is era-neutral: the actual swap-interval call is issued by the
 * per-era mixins (GLFW on the classic line, SDL3 on 26.1+), but the policy and
 * the one-shot reporting live here.</p>
 */
public final class FramePacing {

    /** JVM uptime below which a surface reconfiguration counts as "boot". */
    public static final long BOOT_WINDOW_MILLIS = 30_000L;

    private static final Logger LOGGER = LoggerFactory.getLogger("HyperionFPS/FramePacing");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private FramePacing() {
    }

    /** Whether the JVM is still inside the startup window that ignores VSync. */
    public static boolean inBootPhase() {
        return inBootPhase(ManagementFactory.getRuntimeMXBean().getUptime());
    }

    /** Testable heart of {@link #inBootPhase()}. */
    static boolean inBootPhase(long uptimeMillis) {
        return uptimeMillis >= 0 && uptimeMillis < BOOT_WINDOW_MILLIS;
    }

    /** Reports the first uncapping event; later reconfigurations log nothing. */
    public static void reportUncapped(String mechanism) {
        if (REPORTED.compareAndSet(false, true)) {
            LOGGER.info("Boot-window frame cap removed: swap interval 0 forced via {}", mechanism);
        }
    }

    /** Reports that the platform refused the uncap request (first event only). */
    public static void reportUncappedRejected(String mechanism) {
        if (REPORTED.compareAndSet(false, true)) {
            LOGGER.warn("Swap interval 0 was not applied ({} reported failure); the frame cap may remain", mechanism);
        }
    }
}

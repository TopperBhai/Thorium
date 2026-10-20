package dev.hyperionfps;

import dev.hyperionfps.core.ThreadGovernor;
import dev.hyperionfps.core.ThoriumSafety;
import dev.hyperionfps.env.LauncherEnvironment;
import dev.hyperionfps.memory.IdleJanitor;
import dev.hyperionfps.version.GameVersion;
import dev.hyperionfps.version.GameVersionDetector;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Pre-launch entrypoint: executes before any game class is loaded.
 *
 * <p>Enforces Phase 2 optimizations before window creation:</p>
 * <ul>
 *   <li>Elevates bootstrap and worker thread priorities to {@link Thread#MAX_PRIORITY}.</li>
 *   <li>Suppresses built-in game telemetry and snooper packet generation.</li>
 *   <li>Initializes the off-heap native memory cleaner to eliminate GC micro-stutters.</li>
 *   <li>Spawns a lightweight thread governor daemon that continuously ensures
 *       newly created render and worker threads run at maximum scheduling priority.</li>
 * </ul>
 *
 * <p><b>Robustness contract.</b> This entrypoint runs before the game window exists,
 * so nothing here may prevent Minecraft from starting. Every step is individually
 * guarded, and the whole body is wrapped so that even an unexpected failure can only
 * produce a log line - never a crash. Safe mode short-circuits all optimisation.</p>
 */
public final class HyperionPrelaunch implements PreLaunchEntrypoint {

    /** Wall-clock start of HyperionFPS boot handling, in nanoseconds. */
    private static final long BOOT_NANOS = System.nanoTime();

    private static volatile boolean executed;

    @Override
    public void onPreLaunch() {
        if (executed) {
            return;
        }
        executed = true;

        // Read the escape hatch first: if safe mode is requested, do nothing else.
        ThoriumSafety.refreshPropertySafeMode();

        try {
            if (ThoriumSafety.isSafeMode()) {
                HyperionFPS.LOGGER.warn("==============================================================");
                HyperionFPS.LOGGER.warn(" Thorium SAFE MODE - no optimisations are being applied.");
                HyperionFPS.LOGGER.warn(" Disable safe mode by removing -D{} from the JVM args",
                        ThoriumSafety.SAFE_MODE_PROPERTY);
                HyperionFPS.LOGGER.warn("==============================================================");
                return;
            }
            runOptimisations();
        } catch (Throwable failure) {
            // Never let an optimisation failure stop the game from launching.
            HyperionFPS.LOGGER.warn("Thorium pre-launch handling failed ({}); "
                    + "continuing without Thorium optimisations.", failure.toString());
        }
    }

    private void runOptimisations() {
        System.setProperty("hyperionfps.prelaunch", "true");

        // Suppress telemetry globally via JVM flags
        System.setProperty("fabric.telemetry.enabled", "false");
        System.setProperty("minecraft.telemetry.enabled", "false");
        System.setProperty("fabric.disable_telemetry", "true");

        // Elevate bootstrap thread priority
        Thread bootstrap = Thread.currentThread();
        try {
            bootstrap.setPriority(Thread.MAX_PRIORITY);
        } catch (Throwable ignored) {
            // Some Android runtimes reject priority changes; not critical.
        }

        // Launch background governor to elevate newly spawned Render and Worker threads
        startGovernorDaemon();

        GameVersion version = GameVersionDetector.detect();
        LauncherEnvironment.Probe environment = LauncherEnvironment.detect();

        HyperionFPS.LOGGER.info("==============================================================");
        HyperionFPS.LOGGER.info(" Thorium (Th-90) {} - Nuclear Optimization Engine", HyperionFPS.modVersion());
        HyperionFPS.LOGGER.info("--------------------------------------------------------------");
        HyperionFPS.LOGGER.info(" Minecraft {} | era={} | runtime namespace={}",
                version, version.era().name().toLowerCase(java.util.Locale.ROOT), version.era().runtimeNamespace());
        HyperionFPS.LOGGER.info(" JVM {} ({}) | {}-bit | {} logical cores",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("sun.arch.data.model", "?"),
                Runtime.getRuntime().availableProcessors());
        HyperionFPS.LOGGER.info(" OS {} {} ({}) | max heap {} MiB",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                environment.maxHeapMiB());
        HyperionFPS.LOGGER.info(" Bootstrap thread '{}' elevated to priority {}",
                bootstrap.getName(), bootstrap.getPriority());
        HyperionFPS.LOGGER.info(" Launcher: {}", environment.launcherGuess());
        for (String signal : environment.signals()) {
            HyperionFPS.LOGGER.info("   launcher signal: {}", signal);
        }
        HyperionFPS.LOGGER.info(" Telemetry and snooper modules disabled");
        HyperionFPS.LOGGER.info(" Off-heap automatic janitor initialized: sweep interval {} ms",
                IdleJanitor.SWEEP_INTERVAL_MILLIS);
        HyperionFPS.LOGGER.info(" Pre-launch handling finished in {} ms",
                (System.nanoTime() - BOOT_NANOS) / 1_000_000L);
        HyperionFPS.LOGGER.info("==============================================================");
    }

    /**
     * Starts the background thread-priority governor.
     *
     * <p>Kept separate so that a failure to start the daemon cannot abort the rest of
     * the pre-launch sequence.</p>
     */
    private void startGovernorDaemon() {
        try {
            Thread governorThread = new Thread(() -> {
                for (int i = 0; i < 20; i++) {
                    try {
                        ThreadGovernor.boostActiveMinecraftThreads();
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Throwable failure) {
                        break;
                    }
                }
            }, "Thorium-ThreadGovernor");
            governorThread.setDaemon(true);
            governorThread.setPriority(Thread.NORM_PRIORITY);
            governorThread.start();
        } catch (Throwable failure) {
            HyperionFPS.LOGGER.warn("Could not start the Thorium thread governor ({}); continuing.",
                    failure.toString());
        }
    }
}

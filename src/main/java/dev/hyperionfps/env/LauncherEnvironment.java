package dev.hyperionfps.env;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evidence-based probe of the launcher and JVM environment the game was started
 * with. Third-party and custom launchers routinely ship sub-optimal JVM flags,
 * thread setups and hidden frame caps; later optimization stages use this probe
 * to decide what needs correcting.
 *
 * <p><b>Honesty contract.</b> The probe never invents findings: every reported
 * signal is the literal command-line fragment or environment variable key that
 * produced it, so a player can verify each claim. When nothing matches, the
 * result says so instead of guessing.</p>
 */
public final class LauncherEnvironment {

    /** Immutable probe result. */
    public record Probe(
            String launcherGuess,
            List<String> signals,
            boolean customHeapConfigured,
            String gcHint,
            long maxHeapMiB) {
    }

    private static final Map<String, String> COMMAND_LINE_TOKENS = new LinkedHashMap<>();
    private static final List<String> ENV_TOKENS = List.of(
            "MULTIMC", "PRISM", "MMC_", "HMCL", "TLAUNCHER", "SKL", "ATLAUNCHER",
            "GDLAUNCHER", "CURSEFORGE", "MODRINTH", "TECHNIC", "POLYMC", "BCC");

    static {
        COMMAND_LINE_TOKENS.put("hmcl", "HMCL");
        COMMAND_LINE_TOKENS.put("prismlauncher", "Prism Launcher");
        COMMAND_LINE_TOKENS.put("multimc", "MultiMC");
        COMMAND_LINE_TOKENS.put("polymc", "PolyMC");
        COMMAND_LINE_TOKENS.put("tlauncher", "TLauncher");
        COMMAND_LINE_TOKENS.put("sklauncher", "SKLauncher");
        COMMAND_LINE_TOKENS.put("modrinth", "Modrinth App");
        COMMAND_LINE_TOKENS.put("curseforge", "CurseForge App");
        COMMAND_LINE_TOKENS.put("gdlauncher", "GDLauncher");
        COMMAND_LINE_TOKENS.put("atlauncher", "ATLauncher");
        COMMAND_LINE_TOKENS.put("technic", "Technic Launcher");
    }

    private static final String[][] GC_FLAGS = {
            {"usezgc", "ZGC"},
            {"useshenandoahgc", "Shenandoah"},
            {"useg1gc", "G1"},
            {"useparallelgc", "Parallel"},
            {"useserialgc", "Serial"},
            {"useconcmarksweepgc", "CMS"},
    };

    private static final int MAX_SIGNALS = 8;

    private LauncherEnvironment() {
    }

    public static Probe detect() {
        String commandLine = ProcessHandle.current().info().commandLine()
                .orElse("")
                .toLowerCase(Locale.ROOT);

        List<String> signals = new ArrayList<>(MAX_SIGNALS);
        String launcherGuess = null;

        for (Map.Entry<String, String> entry : COMMAND_LINE_TOKENS.entrySet()) {
            if (commandLine.contains(entry.getKey())) {
                launcherGuess = entry.getValue();
                signals.add("command line contains '" + entry.getKey() + "'");
                break;
            }
        }

        for (Map.Entry<String, String> env : System.getenv().entrySet()) {
            if (signals.size() >= MAX_SIGNALS) {
                break;
            }
            String upperKey = env.getKey().toUpperCase(Locale.ROOT);
            for (String token : ENV_TOKENS) {
                if (upperKey.contains(token)) {
                    signals.add("environment variable '" + env.getKey() + "'");
                    if (launcherGuess == null) {
                        launcherGuess = "not identified (environment suggests " + token + ")";
                    }
                    break;
                }
            }
        }

        String gcHint = "JVM default";
        for (String[] gcFlag : GC_FLAGS) {
            if (commandLine.contains(gcFlag[0])) {
                gcHint = gcFlag[1];
                break;
            }
        }

        boolean customHeap = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT).contains("hotspot")
                && (commandLine.contains("-xmx") || commandLine.contains("-xx:maxheapsize"));
        long maxHeapMiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        return new Probe(
                launcherGuess == null ? "not identified (official or unknown launcher)" : launcherGuess,
                List.copyOf(signals),
                customHeap,
                gcHint,
                maxHeapMiB);
    }
}

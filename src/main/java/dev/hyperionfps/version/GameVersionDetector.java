package dev.hyperionfps.version;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Resolves the running Minecraft version, safely, from any loading phase
 * (including mixin configuration plugins which run very early).
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>{@code -Dhyperionfps.gameVersion=...} system property (packagers, tests)</li>
 *   <li>Fabric loader's {@code minecraft} mod container metadata</li>
 *   <li>{@link GameVersion#parse(String) parse("unknown")}</li>
 * </ol>
 *
 * Never throws: bootstrapping problems degrade to {@link Era#UNKNOWN} which
 * disables all era-specific optimization instead of crashing the game.
 */
public final class GameVersionDetector {

    private static final String OVERRIDE_PROPERTY = "hyperionfps.gameVersion";
    private static final String MINECRAFT_MOD_ID = "minecraft";

    private GameVersionDetector() {
    }

    public static GameVersion detect() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return GameVersion.parse(override);
        }
        try {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MINECRAFT_MOD_ID);
            if (container.isPresent()) {
                return GameVersion.parse(container.get().getMetadata().getVersion().getFriendlyString());
            }
        } catch (Throwable ignored) {
            // Loader not bootstrapped yet (external tooling, unit tests): fall through.
        }
        return GameVersion.parse("unknown");
    }
}

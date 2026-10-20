package dev.hyperionfps.mixin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import dev.hyperionfps.core.ThoriumSafety;
import dev.hyperionfps.version.Era;
import dev.hyperionfps.version.GameVersion;
import dev.hyperionfps.version.GameVersionDetector;

/**
 * Runtime version router for every HyperionFPS mixin configuration.
 *
 * <p>Instead of shipping separate, version-locked mixin jars, the mod ships one
 * configuration per concern and this plugin decides - at launch, against the
 * detected Minecraft version - which mixins are eligible. Mixin classes are
 * organized by family:</p>
 *
 * <ul>
 *   <li>{@code dev.hyperionfps.mixin.core.*} - applies on every supported version</li>
 *   <li>{@code dev.hyperionfps.mixin.legacy.*} - only pre-26.1 (obfuscated era)</li>
 *   <li>{@code dev.hyperionfps.mixin.modern.*} - only 26.1+ (unobfuscated era)</li>
 * </ul>
 *
 * <p>Decisions are counted and reported through {@link #status()} so the main
 * mod initializer can verify in logs that the router came up with the expected
 * routing for the running version.</p>
 */
public final class HyperionMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("HyperionFPS/MixinRouter");

    private static final GameVersion GAME_VERSION = GameVersionDetector.detect();
    private static final Era ERA = GAME_VERSION.era();

    private static final AtomicInteger ROUTED_IN = new AtomicInteger();
    private static final AtomicInteger ROUTED_OUT = new AtomicInteger();
    private static final AtomicInteger APPLIED = new AtomicInteger();

    private String configPackage = "<unknown>";

    private enum Family {
        CORE,
        LEGACY,
        MODERN,
        UNCLASSIFIED
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.configPackage = mixinPackage;

        // Safe mode is honoured here, before a single Minecraft class is touched, so
        // that a player whose game cannot start with Thorium can always recover.
        ThoriumSafety.refreshPropertySafeMode();
        if (ThoriumSafety.isSafeMode()) {
            LOGGER.warn("Thorium safe mode active - ALL mixins will be skipped.");
        }

        LOGGER.info("Mixin router online | minecraft={} | era={} | names={} | package={}",
                GAME_VERSION, ERA.name().toLowerCase(Locale.ROOT), ERA.runtimeNamespace(), mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Safe mode disables the mod entirely, including the core mixins.
        if (ThoriumSafety.isSafeMode()) {
            ROUTED_OUT.incrementAndGet();
            LOGGER.debug("Safe mode active, skipping {}", mixinClassName);
            return false;
        }

        Family family = familyOf(mixinClassName);
        boolean apply = switch (family) {
            case LEGACY -> ERA == Era.LEGACY;
            case MODERN -> ERA == Era.UNOBFUSCATED;
            case CORE, UNCLASSIFIED -> true;
        };

        if (apply) {
            ROUTED_IN.incrementAndGet();
        } else {
            ROUTED_OUT.incrementAndGet();
            LOGGER.debug("Skipping {} ({} family not applicable to era {})",
                    mixinClassName, family, ERA.name().toLowerCase(Locale.ROOT));
        }
        return apply;
    }

    private Family familyOf(String mixinClassName) {
        String prefix = configPackage == null ? "" : configPackage + ".";
        if (mixinClassName.startsWith(prefix + "legacy.")) {
            return Family.LEGACY;
        }
        if (mixinClassName.startsWith(prefix + "modern.")) {
            return Family.MODERN;
        }
        if (mixinClassName.startsWith(prefix + "core.")) {
            return Family.CORE;
        }
        return Family.UNCLASSIFIED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // HyperionFPS does not restrict which other mixins may target its classes.
    }

    @Override
    public List<String> getMixins() {
        // No programmatic mixin list; config files are authoritative.
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Counted on the way out (postApply); nothing to set up on the way in.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        APPLIED.incrementAndGet();
        LOGGER.debug("Applied {} -> {}", mixinClassName, targetClassName);
    }

    /** Routing summary consumed by diagnostics; safe to call from any thread. */
    public static String status() {
        return "minecraft=" + GAME_VERSION
                + " era=" + ERA.name().toLowerCase(Locale.ROOT)
                + " safeMode=" + ThoriumSafety.isSafeMode()
                + " eligible=" + ROUTED_IN
                + " skipped=" + ROUTED_OUT
                + " applied=" + APPLIED;
    }
}

package dev.hyperionfps.version;

/**
 * The two binary-incompatible architecture eras of modern Minecraft, split by
 * the removal of obfuscation (26.1, March 2026).
 *
 * <p>This boundary is the single most important compatibility fact for a
 * client mod: a jar targeted at one era can never load on the other, because
 * runtime class names differ at the bytecode level ({@code net.minecraft.class_310}
 * vs {@code net.minecraft.client.Minecraft}). HyperionFPS therefore builds one
 * artifact per era from one shared source tree, and gates era-specific mixins
 * at runtime through this enum.</p>
 */
public enum Era {

    /** Versions before 26.1: obfuscated at rest, remapped to intermediary at runtime. */
    LEGACY("intermediary"),

    /** Versions from 26.1 onward: ship unobfuscated; Mojang's names at runtime. */
    UNOBFUSCATED("mojmap"),

    /** Undetectable version: all era-specific optimization is skipped. */
    UNKNOWN("unknown");

    private final String runtimeNamespace;

    Era(String runtimeNamespace) {
        this.runtimeNamespace = runtimeNamespace;
    }

    /** Name space the game's classes live in at runtime. */
    public String runtimeNamespace() {
        return runtimeNamespace;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }
}

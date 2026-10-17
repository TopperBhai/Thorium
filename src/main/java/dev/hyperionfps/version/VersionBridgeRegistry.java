package dev.hyperionfps.version;

/**
 * Lazily builds and hands out the process-wide {@link VersionBridge} instance.
 * The bridge is derived from the detected game version at first use and is
 * immutable, so the initialization-on-demand holder idiom is sufficient.
 */
public final class VersionBridgeRegistry {

    private VersionBridgeRegistry() {
    }

    public static VersionBridge get() {
        return Holder.BRIDGE;
    }

    private static final class Holder {
        private static final VersionBridge BRIDGE = new DefaultVersionBridge(GameVersionDetector.detect());
    }
}

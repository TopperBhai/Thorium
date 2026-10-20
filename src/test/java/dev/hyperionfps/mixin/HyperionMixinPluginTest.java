package dev.hyperionfps.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.hyperionfps.core.ThoriumSafety;

class HyperionMixinPluginTest {

    private HyperionMixinPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new HyperionMixinPlugin();
        plugin.onLoad("dev.hyperionfps.mixin");
    }

    @Test
    void coreMixinsAlwaysAllowed() {
        boolean apply = plugin.shouldApplyMixin(
                "net.minecraft.client.Minecraft",
                "dev.hyperionfps.mixin.core.MinecraftThreadCaptureMixin"
        );
        assertTrue(apply, "Core mixin should always apply");
    }

    @Test
    void safeModeDisablesEvenCoreMixins() {
        ThoriumSafety.setConfigSafeMode(true);
        try {
            boolean apply = plugin.shouldApplyMixin(
                    "net.minecraft.client.Minecraft",
                    "dev.hyperionfps.mixin.core.MinecraftThreadCaptureMixin"
            );
            assertFalse(apply, "Safe mode must disable every mixin");
        } finally {
            ThoriumSafety.setConfigSafeMode(false);
        }
    }

    @Test
    void statusReturnsValidDiagnosticString() {
        String status = HyperionMixinPlugin.status();
        assertNotNull(status);
        assertTrue(status.contains("minecraft="));
        assertTrue(status.contains("era="));
        assertTrue(status.contains("safeMode="));
    }
}

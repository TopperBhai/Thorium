package dev.hyperionfps.mixin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void statusReturnsValidDiagnosticString() {
        String status = HyperionMixinPlugin.status();
        assertNotNull(status);
        assertTrue(status.contains("minecraft="));
        assertTrue(status.contains("era="));
    }
}

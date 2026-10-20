package dev.hyperionfps.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThoriumSafetyTest {

    @AfterEach
    void reset() {
        ThoriumSafety.setConfigSafeMode(false);
        System.clearProperty(ThoriumSafety.SAFE_MODE_PROPERTY);
        ThoriumSafety.refreshPropertySafeMode();
    }

    @Test
    void safeModeIsOffByDefault() {
        assertFalse(ThoriumSafety.isSafeMode());
    }

    @Test
    void configCanEnableSafeMode() {
        ThoriumSafety.setConfigSafeMode(true);
        assertTrue(ThoriumSafety.isSafeMode());
    }

    @Test
    void configCanDisableSafeModeAgain() {
        ThoriumSafety.setConfigSafeMode(true);
        ThoriumSafety.setConfigSafeMode(false);
        assertFalse(ThoriumSafety.isSafeMode());
    }

    @Test
    void systemPropertyEnablesSafeMode() {
        System.setProperty(ThoriumSafety.SAFE_MODE_PROPERTY, "true");
        ThoriumSafety.refreshPropertySafeMode();

        assertTrue(ThoriumSafety.isSafeMode());
        assertTrue(ThoriumSafety.refreshPropertySafeModeAndCheck());
    }

    @Test
    void systemPropertyAndConfigAreAdditive() {
        ThoriumSafety.setConfigSafeMode(true);
        assertTrue(ThoriumSafety.isSafeMode());

        ThoriumSafety.setConfigSafeMode(false);
        assertFalse(ThoriumSafety.isSafeMode(), "property was never set in this test");
    }
}

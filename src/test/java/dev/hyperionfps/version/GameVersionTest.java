package dev.hyperionfps.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks down the version parsing and era classification the whole mixin
 * routing strategy depends on.
 */
class GameVersionTest {

    @Test
    void parsesClassicReleaseLine() {
        GameVersion version = GameVersion.parse("1.21.1");
        assertTrue(version.isKnown());
        assertEquals(GameVersion.Scheme.OLD_LINE, version.scheme());
        assertEquals(Era.LEGACY, version.era());
        assertTrue(version.isAtLeast(1, 21));
        assertTrue(version.isAtLeast(1, 21, 1));
        assertFalse(version.isAtLeast("1.21.2"));
    }

    @Test
    void eraBoundaryIsExactlyTwentySixDotOne() {
        assertEquals(Era.LEGACY, GameVersion.parse("26.0.9").era());
        assertEquals(Era.UNOBFUSCATED, GameVersion.parse("26.1").era());
        assertEquals(Era.UNOBFUSCATED, GameVersion.parse("26.3").era());
        assertEquals(Era.UNOBFUSCATED, GameVersion.parse("26.3.1").era());
    }

    @Test
    void yearDropOrderingHandlesHotfixes() {
        assertTrue(GameVersion.parse("26.3.1").isAtLeast("26.3"));
        assertTrue(GameVersion.parse("26.3.1").isAtLeast(26, 3));
        assertFalse(GameVersion.parse("26.3").isAtLeast("26.3.1"));
    }

    @Test
    void devSnapshotsOfYearDropsCountAsUnobfuscated() {
        GameVersion version = GameVersion.parse("26.4-snapshot-3");
        assertEquals(GameVersion.Scheme.DEV_SNAPSHOT, version.scheme());
        assertEquals(Era.UNOBFUSCATED, version.era());
        assertTrue(version.isAtLeast("26.1"));
    }

    @Test
    void weeklySnapshotsCountAsLegacy() {
        GameVersion version = GameVersion.parse("25w46a");
        assertEquals(GameVersion.Scheme.WEEKLY_SNAPSHOT, version.scheme());
        assertEquals(Era.LEGACY, version.era());
        assertTrue(version.isKnown());
    }

    @Test
    void unparseableVersionsAreUnknownAndCompareBelowEverything() {
        GameVersion version = GameVersion.parse("not-a-version");
        assertFalse(version.isKnown());
        assertEquals(Era.UNKNOWN, version.era());
        assertFalse(version.isAtLeast(1, 0));
        assertFalse(version.isAtLeast("1.0"));
    }

    @Test
    void equalityIgnoresTrailingZeroSegmentsConsistently() {
        assertEquals(GameVersion.parse("26.3"), GameVersion.parse("26.3.0"));
        assertEquals(GameVersion.parse("26.3").hashCode(), GameVersion.parse("26.3.0").hashCode());
        assertNotEquals(GameVersion.parse("26.3"), GameVersion.parse("26.3.1"));
    }
}

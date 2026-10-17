package dev.hyperionfps.version;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable Minecraft version value that understands both numbering systems the
 * game has shipped under:
 *
 * <ul>
 *   <li>the classic line: {@code 1.16.5 ... 1.21.11}</li>
 *   <li>the year-drop scheme introduced December 2025: {@code 26.1}, {@code 26.3},
 *       with hotfixes as a third segment ({@code 26.1.2}) and snapshots such as
 *       {@code 26.4-snapshot-3}</li>
 * </ul>
 *
 * Parsing is deliberately lenient: weekly snapshots ({@code 25w46a}), release
 * candidates, pre-releases and experimental builds all resolve to comparable
 * numeric segments. Era classification (see {@link #era()}) only depends on the
 * obfuscation boundary at 26.1 and is exact even for snapshots.
 */
public final class GameVersion implements Comparable<GameVersion> {

    /** Numbering scheme a version string belongs to. */
    public enum Scheme {
        /** Classic {@code 1.x} release line. */
        OLD_LINE,
        /** Year-drop release ({@code 26.x}). */
        YEAR_DROP,
        /** Old weekly snapshot ({@code 17w..-25w46a}). */
        WEEKLY_SNAPSHOT,
        /** Snapshot of a year-drop ({@code 26.4-snapshot-3}). */
        DEV_SNAPSHOT,
        /** Not parseable / not available yet. */
        UNKNOWN
    }

    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)");
    private static final Pattern WEEKLY_SNAPSHOT = Pattern.compile("^(\\d{2})w(\\d{2})([a-z])$");

    private final String raw;
    private final Scheme scheme;
    private final int[] segments;

    private GameVersion(String raw, Scheme scheme, int[] segments) {
        this.raw = raw;
        this.scheme = scheme;
        this.segments = segments;
    }

    /** Parses any Minecraft version string; never returns {@code null}. */
    public static GameVersion parse(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            return new GameVersion("unknown", Scheme.UNKNOWN, new int[0]);
        }
        String trimmed = rawVersion.trim();

        Matcher weekly = WEEKLY_SNAPSHOT.matcher(trimmed);
        if (weekly.matches()) {
            int week = Integer.parseInt(weekly.group(2));
            // Weekly snapshots belong to the pre-26.1 (legacy) era. They are
            // mapped to their release line coarsely - sufficient for era
            // classification and "before/after 1.21" style checks.
            return new GameVersion(trimmed, Scheme.WEEKLY_SNAPSHOT, new int[] {1, 21, 0, week});
        }

        Matcher numeric = NUMERIC_PREFIX.matcher(trimmed);
        if (!numeric.find()) {
            return new GameVersion(trimmed, Scheme.UNKNOWN, new int[0]);
        }
        String[] parts = numeric.group(1).split("\\.");
        int[] parsed = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                parsed[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException invalid) {
                return new GameVersion(trimmed, Scheme.UNKNOWN, new int[0]);
            }
        }

        Scheme scheme;
        if (trimmed.toLowerCase(Locale.ROOT).contains("snapshot")) {
            scheme = Scheme.DEV_SNAPSHOT;
        } else if (parsed.length > 0 && parsed[0] >= 26) {
            scheme = Scheme.YEAR_DROP;
        } else {
            scheme = Scheme.OLD_LINE;
        }
        return new GameVersion(trimmed, scheme, parsed);
    }

    /** True when this instance resolved to real numeric segments. */
    public boolean isKnown() {
        return scheme != Scheme.UNKNOWN;
    }

    /**
     * Architecture era of this version. The boundary is the obfuscation change:
     * every build from 26.1 (including its snapshots) onward is unobfuscated.
     */
    public Era era() {
        if (!isKnown()) {
            return Era.UNKNOWN;
        }
        return isAtLeast(26, 1) ? Era.UNOBFUSCATED : Era.LEGACY;
    }

    /** {@code version >= major.minor.patch} */
    public boolean isAtLeast(int major, int minor, int patch) {
        if (!isKnown()) {
            return false;
        }
        return compareSegments(segments, new int[] {major, minor, patch}) >= 0;
    }

    /** {@code version >= major.minor} */
    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /** {@code version >= spec} where spec is any parseable version string. */
    public boolean isAtLeast(String spec) {
        GameVersion other = parse(spec);
        if (!isKnown() || !other.isKnown()) {
            return false;
        }
        return compareTo(other) >= 0;
    }

    public Scheme scheme() {
        return scheme;
    }

    public String raw() {
        return raw;
    }

    @Override
    public int compareTo(GameVersion other) {
        if (scheme == Scheme.UNKNOWN || other.scheme == Scheme.UNKNOWN) {
            if (scheme == other.scheme) {
                return 0;
            }
            return scheme == Scheme.UNKNOWN ? -1 : 1;
        }
        return compareSegments(segments, other.segments);
    }

    private static int compareSegments(int[] left, int[] right) {
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            int result = Integer.compare(l, r);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof GameVersion other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        // equals() compares with implicit trailing-zero padding ("26.3" equals
        // "26.3.0"), so hashing must normalize the same way to keep the contract.
        int length = segments.length;
        while (length > 1 && segments[length - 1] == 0) {
            length--;
        }
        int hash = 1;
        for (int i = 0; i < length; i++) {
            hash = hash * 31 + segments[i];
        }
        return hash;
    }

    @Override
    public String toString() {
        return raw;
    }
}

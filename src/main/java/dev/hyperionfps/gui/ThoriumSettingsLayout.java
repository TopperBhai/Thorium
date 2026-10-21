package dev.hyperionfps.gui;

/**
 * Layout maths for the Thorium settings console.
 *
 * <p>The settings rows live in a band between the header and the footer bar. When the
 * window is too short for the whole list - a 220 pixel high window fits barely five
 * of the eight Advanced rows - the list scrolls inside that band instead of
 * spilling over the footer. The band and the scroll range are computed here, in
 * plain integers, so the behaviour can be unit tested without a Minecraft
 * bootstrap.</p>
 *
 * <p>Every per-version {@code ThoriumVideoSettingsScreen} uses this class, so the
 * console behaves identically on all five supported Minecraft versions.</p>
 */
public final class ThoriumSettingsLayout {

    /** Height of a single settings row, in GUI pixels. */
    public static final int ROW_HEIGHT = 23;

    /** Vertical gap between two settings rows, in GUI pixels. */
    public static final int ROW_GAP = 4;

    /** Distance between the top of a row and the top of the next row. */
    public static final int ROW_STEP = ROW_HEIGHT + ROW_GAP;

    /** Height of the footer bar at the bottom of the screen, in GUI pixels. */
    public static final int FOOTER_HEIGHT = 28;

    /** Gap between the bottom of the content band and the footer bar, in GUI pixels. */
    public static final int FOOTER_GAP = 6;

    /** Top edge of the content band, in GUI pixels. */
    public static final int CONTENT_TOP = 44;

    private ThoriumSettingsLayout() {
    }

    /** Top edge of the content band. */
    public static int contentTop() {
        return CONTENT_TOP;
    }

    /**
     * Bottom edge of the content band.
     *
     * <p>The band always ends above the footer bar and always keeps room for at
     * least one row, so the list stays usable even on a very short window.</p>
     */
    public static int contentBottom(int screenHeight) {
        return Math.max(CONTENT_TOP + ROW_HEIGHT, screenHeight - FOOTER_HEIGHT - FOOTER_GAP);
    }

    /** Y position of the row with the given index when the list is not scrolled. */
    public static int rowY(int index) {
        return CONTENT_TOP + index * ROW_STEP;
    }

    /**
     * The largest scroll offset that still shows the end of the list.
     *
     * <p>Zero means the whole list fits and the list must not scroll at all.</p>
     */
    public static int maxScrollOffset(int rowCount, int screenHeight) {
        if (rowCount <= 0) {
            return 0;
        }
        int lastRowBottom = rowY(rowCount - 1) + ROW_HEIGHT;
        return Math.max(0, lastRowBottom - contentBottom(screenHeight));
    }

    /** Clamps a scroll offset to the range the list can actually be scrolled through. */
    public static int clampScrollOffset(int offset, int rowCount, int screenHeight) {
        return Math.min(Math.max(0, offset), maxScrollOffset(rowCount, screenHeight));
    }

    /** Whether the row at the given position is (at least partly) inside the band. */
    public static boolean isRowVisible(int rowY, int screenHeight) {
        return rowY + ROW_HEIGHT > CONTENT_TOP && rowY < contentBottom(screenHeight);
    }
}

package dev.hyperionfps.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the settings console layout maths.
 */
class ThoriumSettingsLayoutTest {

    /** The number of rows in the longest tab (Advanced). */
    private static final int LONGEST_TAB_ROWS = 8;

    /** A window height at which the longest tab does not fit into the content band. */
    private static final int SHORT_HEIGHT = 220;

    @Test
    void contentBandEndsAboveTheFooterBar() {
        int footerTop = SHORT_HEIGHT - ThoriumSettingsLayout.FOOTER_HEIGHT;
        assertTrue(ThoriumSettingsLayout.contentBottom(SHORT_HEIGHT) < footerTop,
                "the content band must end above the footer bar");
    }

    @Test
    void everyRowStaysReachableOnAShortWindow() {
        int maxScroll = ThoriumSettingsLayout.maxScrollOffset(LONGEST_TAB_ROWS, SHORT_HEIGHT);
        assertTrue(maxScroll > 0, "the list has to scroll on a " + SHORT_HEIGHT + " pixel high window");

        for (int index = 0; index < LONGEST_TAB_ROWS; index++) {
            int baseY = ThoriumSettingsLayout.rowY(index);
            boolean reachable = false;
            for (int offset = 0; offset <= maxScroll && !reachable; offset++) {
                reachable = ThoriumSettingsLayout.isRowVisible(baseY - offset, SHORT_HEIGHT);
            }
            assertTrue(reachable, "row " + index + " must stay reachable");
        }
    }

    @Test
    void theLastRowIsVisibleAtTheMaximumScrollOffset() {
        int maxScroll = ThoriumSettingsLayout.maxScrollOffset(LONGEST_TAB_ROWS, SHORT_HEIGHT);
        int lastRowY = ThoriumSettingsLayout.rowY(LONGEST_TAB_ROWS - 1) - maxScroll;
        assertTrue(ThoriumSettingsLayout.isRowVisible(lastRowY, SHORT_HEIGHT),
                "the last row must be visible when the list is scrolled to the end");
    }

    @Test
    void aTallWindowNeedsNoScrolling() {
        assertEquals(0, ThoriumSettingsLayout.maxScrollOffset(LONGEST_TAB_ROWS, 480));
    }

    @Test
    void scrollOffsetIsClampedToTheList() {
        int maxScroll = ThoriumSettingsLayout.maxScrollOffset(LONGEST_TAB_ROWS, SHORT_HEIGHT);
        assertEquals(0, ThoriumSettingsLayout.clampScrollOffset(-40, LONGEST_TAB_ROWS, SHORT_HEIGHT));
        assertEquals(maxScroll,
                ThoriumSettingsLayout.clampScrollOffset(maxScroll + 500, LONGEST_TAB_ROWS, SHORT_HEIGHT));
        assertEquals(10, ThoriumSettingsLayout.clampScrollOffset(10, LONGEST_TAB_ROWS, SHORT_HEIGHT));
    }

    @Test
    void rowsOutsideTheBandAreHidden() {
        int bottom = ThoriumSettingsLayout.contentBottom(SHORT_HEIGHT);
        assertFalse(ThoriumSettingsLayout.isRowVisible(bottom, SHORT_HEIGHT));
        assertFalse(ThoriumSettingsLayout.isRowVisible(
                ThoriumSettingsLayout.contentTop() - ThoriumSettingsLayout.ROW_HEIGHT, SHORT_HEIGHT));
        assertTrue(ThoriumSettingsLayout.isRowVisible(ThoriumSettingsLayout.contentTop(), SHORT_HEIGHT));
    }

    @Test
    void anEmptyListNeverScrolls() {
        assertEquals(0, ThoriumSettingsLayout.maxScrollOffset(0, SHORT_HEIGHT));
        assertEquals(0, ThoriumSettingsLayout.clampScrollOffset(50, 0, SHORT_HEIGHT));
    }
}

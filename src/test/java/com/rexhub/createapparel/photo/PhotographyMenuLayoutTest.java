package com.rexhub.createapparel.photo;

import org.junit.jupiter.api.Test;

import static com.rexhub.createapparel.photo.PhotographyMenuLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class PhotographyMenuLayoutTest {
    @Test void fitsVanillaMinimumGuiAndSeparatesFourUpperRegions() {
        for (int[] screen : new int[][]{{320, 240}, {426, 240}, {640, 360}, {960, 540}}) {
            assertTrue((screen[0] - WIDTH) / 2 >= 0);
            assertTrue((screen[1] - HEIGHT) / 2 >= 0);
            assertTrue((screen[1] - CONSENT_HEIGHT) / 2 >= 0);
        }
        assertTrue(GARMENT_LEFT >= 4);
        assertTrue(PREVIEW_X + PREVIEW_WIDTH < STYLE_X);
        assertTrue(STYLE_X + STYLE_WIDTH < SETTINGS_X);
        assertTrue(SETTINGS_X + SETTINGS_WIDTH < WIDTH);
        assertTrue(STYLE_Y + 4 * 20 + BUTTON_HEIGHT < INVENTORY_Y);
        assertTrue(PREVIEW_Y + PREVIEW_HEIGHT <= INVENTORY_Y - 12);
        assertEquals(5, PREVIEW_Y);
        assertEquals(PREVIEW_Y, SETTINGS_Y);
        assertEquals(18, BUTTON_HEIGHT);
        assertTrue(SETTINGS_Y + 7 * BUTTON_STEP + BUTTON_HEIGHT < OFFER_Y);
        assertTrue(BUTTON_HEIGHT <= BUTTON_STEP);
        assertTrue(INVENTORY_Y + 58 + 16 < HEIGHT);
    }

    @Test void balancesOuterMarginsAndMatchesCraftingTableTradeSpacing() {
        int leftInventoryFrame = INVENTORY_X - 1;
        int rightResultFrame = RESULT_X + 21;
        assertEquals(leftInventoryFrame, WIDTH - rightResultFrame);
        assertTrue(PAYMENT_X - 1 > INVENTORY_X + 8 * 18 + 17);
        assertEquals(18, PAYMENT_X - INVENTORY_X - 9 * 18);
        assertEquals(124 - 66, RESULT_X - PAYMENT_X);
        assertTrue(TRADE_Y + 21 < HEIGHT);
        assertTrue(OFFER_Y + 17 < TRADE_Y - 5);
    }

    @Test void centersTheWardrobeGridWithinTheGarmentArea() {
        int leftFrame = 12 + GARMENT_X_OFFSET - 1;
        int rightFrame = 72 + GARMENT_X_OFFSET + 17;
        assertEquals(PREVIEW_X, leftFrame + rightFrame);
        assertTrue(RETURN_NOTE_Y >= 18 + 4 * 20 + 18);
        assertTrue(RETURN_NOTE_Y + 2 * 9 * 0.65 < INVENTORY_Y - 12);
    }
}

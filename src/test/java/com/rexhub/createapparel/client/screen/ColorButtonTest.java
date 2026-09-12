package com.rexhub.createapparel.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorButtonTest {
    @Test
    void shortLabelsAreCenteredAcrossTheWholeButton() {
        assertEquals(80, ColorButton.textLeft(200, 40));
        assertEquals(28, ColorButton.textLeft(96, 40));
    }

    @Test
    void overlappingLabelsMoveOnlyAsFarAsTheSwatchMargin() {
        assertEquals(20, ColorButton.textLeft(96, 60));
        assertEquals(20, ColorButton.textLeft(96, 72));
        assertEquals(20, ColorButton.textLeft(96, 56));
    }
}

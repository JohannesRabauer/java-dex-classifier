package org.javadex.ml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CenterSquareTest {

    @Test
    void squareInputIsKeptWhole() {
        var crop = CenterSquare.of(100, 100);
        assertEquals(0, crop.x());
        assertEquals(0, crop.y());
        assertEquals(100, crop.size());
    }

    @Test
    void wideInputCropsHorizontallyCentered() {
        var crop = CenterSquare.of(1920, 1080);
        assertEquals(420, crop.x());
        assertEquals(0, crop.y());
        assertEquals(1080, crop.size());
    }

    @Test
    void tallInputCropsVerticallyCentered() {
        var crop = CenterSquare.of(1080, 1920);
        assertEquals(0, crop.x());
        assertEquals(420, crop.y());
        assertEquals(1080, crop.size());
    }
}

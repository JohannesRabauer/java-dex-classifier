package org.javadex.ml;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogitsTranslatorTest {

    @Test
    void wideImageIsCenterCroppedNotStretched() {
        BufferedImage wide = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        // left half black, right half white -> a center crop must contain both
        for (int x = 0; x < 120; x++) {
            for (int y = 0; y < 60; y++) {
                wide.setRGB(x, y, x < 60 ? 0x000000 : 0xFFFFFF);
            }
        }
        Image image = ImageFactory.getInstance().fromImage(wide);

        Image cropped = LogitsTranslator.centerSquare(image);

        assertEquals(60, cropped.getWidth());
        assertEquals(60, cropped.getHeight());
    }
}

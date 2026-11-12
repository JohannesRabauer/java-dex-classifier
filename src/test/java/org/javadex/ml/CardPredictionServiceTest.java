package org.javadex.ml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardPredictionServiceTest {

    @Test
    void softmaxSumsToOneAndKeepsOrder() {
        float[] softmax = CardPredictionService.softmax(new float[]{2.0f, 1.0f, 0.0f});
        assertEquals(1.0, softmax[0] + softmax[1] + softmax[2], 1e-6);
        assertTrue(softmax[0] > softmax[1] && softmax[1] > softmax[2]);
    }
}

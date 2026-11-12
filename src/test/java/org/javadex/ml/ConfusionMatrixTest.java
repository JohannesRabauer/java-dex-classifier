package org.javadex.ml;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfusionMatrixTest {

    @Test
    void buildsMatrixAndPerClassAccuracy() {
        // classes: a, b, c
        // row = expected, column = actual
        // a: 2 correct, 1 misread as b -> 2/3
        // b: 1 correct, 1 misread as c -> 1/2
        // c: 3 correct -> 3/3
        ConfusionMatrix matrix = new ConfusionMatrix(List.of("a", "b", "c"), List.of(
                new ConfusionMatrix.Record("a", "a"),
                new ConfusionMatrix.Record("a", "a"),
                new ConfusionMatrix.Record("a", "b"),
                new ConfusionMatrix.Record("b", "b"),
                new ConfusionMatrix.Record("b", "c"),
                new ConfusionMatrix.Record("c", "c"),
                new ConfusionMatrix.Record("c", "c"),
                new ConfusionMatrix.Record("c", "c")
        ));

        assertArrayEquals(new int[][]{
                {2, 1, 0},
                {0, 1, 1},
                {0, 0, 3}
        }, matrix.matrix());

        assertEquals(6.0 / 8, matrix.overallAccuracy(), 1e-9);
        var perClass = matrix.perClassAccuracy();
        assertEquals(2.0 / 3, perClass.get("a"), 1e-9);
        assertEquals(0.5, perClass.get("b"), 1e-9);
        assertEquals(1.0, perClass.get("c"), 1e-9);
    }

    @Test
    void classWithNoSamplesHasZeroAccuracy() {
        ConfusionMatrix matrix = new ConfusionMatrix(List.of("a", "b"), List.of());
        assertEquals(0.0, matrix.perClassAccuracy().get("a"));
        assertEquals(0.0, matrix.overallAccuracy());
    }
}

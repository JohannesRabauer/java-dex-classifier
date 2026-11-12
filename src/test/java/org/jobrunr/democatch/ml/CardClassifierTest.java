package org.jobrunr.democatch.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CardClassifierTest {

    @TempDir
    Path tempDir;

    private static CardClassifier classifier() {
        return new CardClassifier(List.of("jd01", "jd02"),
                new float[][]{{1f, 0f, 0f}, {0f, 1f, 0f}}, new float[]{0f, 0.5f});
    }

    @Test
    void logitsAreBiasPlusWeightTimesFeatures() {
        assertArrayEquals(new float[]{2f, 3.5f}, classifier().logits(new float[]{2f, 3f, 9f}), 1e-6f);
    }

    @Test
    void picksTheClassWithTheHighestLogit() {
        assertEquals("jd01", classifier().bestLabel(new float[]{5f, 1f, 0f}));
        assertEquals("jd02", classifier().bestLabel(new float[]{0f, 1f, 0f}));
    }

    @Test
    void survivesWritingAndLoading() throws Exception {
        classifier().writeHead(tempDir.resolve("head.jd"));
        Files.write(tempDir.resolve("classes.txt"), List.of("jd01", "jd02"));

        CardClassifier loaded = CardClassifier.load(tempDir);
        assertEquals(List.of("jd01", "jd02"), loaded.classes());
        assertArrayEquals(classifier().logits(new float[]{2f, 3f, 9f}), loaded.logits(new float[]{2f, 3f, 9f}), 1e-6f);
    }

    @Test
    void loadingWithoutModelSaysToTrainFirst() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> CardClassifier.load(tempDir));
        assertTrue(ex.getMessage().contains("train"), ex.getMessage());
    }

    @Test
    void rejectsFeaturesOfTheWrongSize() {
        assertThrows(IllegalArgumentException.class, () -> classifier().logits(new float[]{1f}));
    }
}

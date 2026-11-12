package org.javadex.jobs;

import org.javadex.JavaDexApplicationConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardEvaluationJobTest {

    @TempDir
    Path tempDir;

    @Test
    void failsWithClearMessageWhenNoModelTrained() {
        CardEvaluationJob job = new CardEvaluationJob(
                new JavaDexApplicationConfiguration(tempDir.toString()), new ObjectMapper());
        IllegalStateException ex = assertThrows(IllegalStateException.class, job::requireModel);
        assertTrue(ex.getMessage().contains("train"), ex.getMessage());
    }

    @Test
    void acceptsHeadFile() throws Exception {
        Path modelDir = tempDir.resolve("model");
        Files.createDirectories(modelDir);
        Files.write(modelDir.resolve("head.jd"), new byte[]{1});
        CardEvaluationJob job = new CardEvaluationJob(
                new JavaDexApplicationConfiguration(tempDir.toString()), new ObjectMapper());
        assertEquals(modelDir, job.requireModel());
    }
}

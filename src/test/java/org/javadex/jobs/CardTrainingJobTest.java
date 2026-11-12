package org.javadex.jobs;

import org.javadex.JavaDexApplicationConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTrainingJobTest {

    @TempDir
    Path tempDir;

    private CardTrainingJob job() {
        return new CardTrainingJob(new JavaDexApplicationConfiguration(tempDir.toString()));
    }

    private void writeImage(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), new byte[]{1, 2, 3});
    }

    @Test
    void rejectsFewerThanTwoClasses() throws Exception {
        Path train = tempDir.resolve("dataset").resolve("train");
        Path valid = tempDir.resolve("dataset").resolve("valid");
        writeImage(train.resolve("jd01"), "a.jpg");
        writeImage(valid.resolve("jd01"), "a.jpg");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> job().validateClasses());
        assertTrue(ex.getMessage().contains("at least two"), ex.getMessage());
    }

    @Test
    void rejectsMismatchedClassSets() throws Exception {
        Path train = tempDir.resolve("dataset").resolve("train");
        Path valid = tempDir.resolve("dataset").resolve("valid");
        writeImage(train.resolve("jd01"), "a.jpg");
        writeImage(train.resolve("jd02"), "a.jpg");
        writeImage(valid.resolve("jd01"), "a.jpg");
        writeImage(valid.resolve("jd03"), "a.jpg");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> job().validateClasses());
        assertTrue(ex.getMessage().contains("jd02") && ex.getMessage().contains("jd03"), ex.getMessage());
    }

    @Test
    void returnsSortedTrainClasses() throws Exception {
        Path train = tempDir.resolve("dataset").resolve("train");
        Path valid = tempDir.resolve("dataset").resolve("valid");
        writeImage(train.resolve("jd02"), "a.jpg");
        writeImage(train.resolve("jd01"), "a.jpg");
        writeImage(valid.resolve("jd01"), "a.jpg");
        writeImage(valid.resolve("jd02"), "a.jpg");

        assertEquals(List.of("jd01", "jd02"), job().validateClasses());
    }
}

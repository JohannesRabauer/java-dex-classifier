package org.javadex.jobs;

import org.javadex.JavaDexApplicationConfiguration;
import org.javadex.catalog.CardCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardDataPreparationJobTest {

    @TempDir
    Path tempDir;

    private Path capturesDir() throws IOException {
        Path captures = tempDir.resolve("captures");
        Files.createDirectories(captures);
        return captures;
    }

    private void writeImage(Path dir, String name, int w, int h) throws IOException {
        Files.createDirectories(dir);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", dir.resolve(name).toFile());
    }

    private CardDataPreparationJob job(Path captures) {
        return new CardDataPreparationJob(
                new CardCatalog(new ObjectMapper(), "cards"),
                new JavaDexApplicationConfiguration(tempDir.toString()) {
                    @Override
                    public Path capturesPath() {
                        return captures;
                    }
                });
    }

    @Test
    void splitsTenImagesIntoEightTrainAndTwoValid() throws Exception {
        Path captures = capturesDir();
        for (int i = 0; i < 10; i++) {
            writeImage(captures.resolve("jd01"), "img_" + i + ".jpg", 64, 96);
        }
        CardDataPreparationJob prepared = job(captures);
        prepared.prepareDataset(List.of(captures.resolve("jd01")), () -> {});

        Path train = tempDir.resolve("dataset").resolve("train").resolve("jd01");
        Path valid = tempDir.resolve("dataset").resolve("valid").resolve("jd01");
        try (var t = Files.list(train)) {
            assertEquals(8, t.count());
        }
        try (var v = Files.list(valid)) {
            assertEquals(2, v.count());
        }
        BufferedImage resized = ImageIO.read(Files.list(train).findFirst().orElseThrow().toFile());
        assertEquals(256, resized.getWidth());
        assertEquals(256, resized.getHeight());
    }

    @Test
    void rejectsClassWithTooFewImages() throws Exception {
        Path captures = capturesDir();
        writeImage(captures.resolve("jd02"), "img_0.jpg", 32, 32);
        writeImage(captures.resolve("jd02"), "img_1.jpg", 32, 32);

        CardDataPreparationJob prepared = job(captures);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> prepared.prepareDataset(List.of(captures.resolve("jd02")), () -> {}));
        assertTrue(ex.getMessage().contains("jd02"), "message must name the offending card: " + ex.getMessage());
    }

    @Test
    void ignoresFoldersThatAreNotCards() throws Exception {
        Path captures = capturesDir();
        for (int i = 0; i < 5; i++) {
            writeImage(captures.resolve("junk"), "img_" + i + ".jpg", 32, 32);
        }
        writeImage(captures.resolve("jd01"), "img_0.jpg", 32, 32);
        writeImage(captures.resolve("jd01"), "img_1.jpg", 32, 32);
        writeImage(captures.resolve("jd01"), "img_2.jpg", 32, 32);

        job(captures).prepareDataset(job(captures).cardClassDirs(captures), () -> {});

        assertTrue(Files.notExists(tempDir.resolve("dataset").resolve("train").resolve("junk")));
    }
}

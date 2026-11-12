package org.javadex.jobs;

import org.javadex.JavaDexApplicationConfiguration;
import org.javadex.catalog.CardCatalog;
import org.javadex.ml.CenterSquare;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
public class CardDataPreparationJob {

    public static final int TARGET_SIZE = 256;

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(CardDataPreparationJob.class));

    private final CardCatalog cards;
    private final JavaDexApplicationConfiguration config;

    public CardDataPreparationJob(CardCatalog cards, JavaDexApplicationConfiguration config) {
        this.cards = cards;
        this.config = config;
    }

    @Job(name = "Prepare card images", retries = 0)
    public void prepare(JobContext jobContext) throws IOException {
        List<Path> classDirs = cardClassDirs(config.capturesPath());
        var progressBar = jobContext.progressBar(classDirs.size());
        prepareDataset(classDirs, progressBar::incrementSucceeded);
    }

    void prepareDataset(List<Path> classDirs, Runnable onClassDone) throws IOException {
        LOGGER.info("Preparing {} card classes", classDirs.size());

        Path trainRoot = config.trainingDatasetPath();
        Path validRoot = config.validationDatasetPath();
        deleteRecursively(trainRoot);
        deleteRecursively(validRoot);
        Files.createDirectories(trainRoot);
        Files.createDirectories(validRoot);

        for (Path classDir : classDirs) {
            String cardId = classDir.getFileName().toString();
            List<Path> images = sortedImages(classDir);
            if (images.size() < 3) {
                throw new IllegalStateException(
                        "Card " + cardId + " has only " + images.size()
                                + " capture(s); at least 3 are needed to train (capture more pictures)");
            }

            int splitIndex = trainCount(images.size());
            List<Path> train = images.subList(0, splitIndex);
            List<Path> valid = images.subList(splitIndex, images.size());
            LOGGER.info("{}: {} train, {} valid", cardId, train.size(), valid.size());

            copyResized(train, trainRoot.resolve(cardId));
            copyResized(valid, validRoot.resolve(cardId));
            onClassDone.run();
        }

        LOGGER.info("Dataset ready: {} / {}", trainRoot, validRoot);
    }

    List<Path> cardClassDirs(Path captures) throws IOException {
        if (!Files.isDirectory(captures)) {
            throw new IllegalStateException("No captures found in " + captures.toAbsolutePath() + " — capture card pictures first");
        }
        List<Path> classDirs;
        try (var dirs = Files.list(captures)) {
            classDirs = dirs.filter(Files::isDirectory)
                    .filter(d -> cards.exists(d.getFileName().toString()))
                    .sorted()
                    .toList();
        }
        if (classDirs.isEmpty()) {
            throw new IllegalStateException("No captured card images found in " + captures.toAbsolutePath());
        }
        return classDirs;
    }

    static int trainCount(int size) {
        return Math.max(1, Math.round(size * 0.8f));
    }

    static List<Path> sortedImages(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private void copyResized(List<Path> images, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (Path image : images) {
            BufferedImage resized = squareResize(ImageIO.read(image.toFile()), TARGET_SIZE);
            ImageIO.write(resized, "jpg", targetDir.resolve(image.getFileName() + ".jpg").toFile());
        }
    }

    static BufferedImage squareResize(BufferedImage source, int size) {
        if (source == null) {
            throw new IllegalStateException("Not a readable image");
        }
        var crop = CenterSquare.of(source.getWidth(), source.getHeight());
        BufferedImage square = source.getSubimage(crop.x(), crop.y(), crop.size(), crop.size());
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(square, 0, 0, size, size, null);
        graphics.dispose();
        return target;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot delete " + p, e);
                }
            });
        }
    }
}

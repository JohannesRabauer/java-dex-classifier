package org.jobrunr.democatch.jobs;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.jobrunr.democatch.catalog.Card;
import org.jobrunr.democatch.catalog.CardCatalog;
import org.jobrunr.democatch.config.AppPaths;
import org.jobrunr.democatch.ml.Backbone;
import org.jobrunr.democatch.ml.CardClassifier;
import org.jobrunr.democatch.ml.ConfusionMatrix;
import org.jobrunr.democatch.ml.HeadTrainer;
import org.jobrunr.democatch.synth.CardImageSynthesizer;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobDashboardProgressBar;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.djl.modality.cv.ImageFactory;

/**
 * Trains the card classifier without a single photo taken by hand:
 * <ol>
 *   <li>per card, draw fake webcam pictures from the card's PNG ({@link CardImageSynthesizer}) and run them through
 *       the frozen ResNet18, which gives 512 numbers per picture</li>
 *   <li>train one small layer on those numbers</li>
 *   <li>check it on pictures it has never seen (drawn with other random numbers) and write the report</li>
 * </ol>
 * Real photos in {@code data/captures/<card>} (from the capture page) are added to the training data when
 * {@code app.train.use-captures=true}. Off by default: the drawn pictures alone are the point.
 */
@Component
public class CardTrainingJob {

    static final int BATCH_SIZE = 64;
    static final float LEARNING_RATE = 1e-2f;
    static final float WEIGHT_DECAY = 0f;

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(CardTrainingJob.class));

    private final AppPaths paths;
    private final CardCatalog cards;
    private final Backbone backbone;
    private final TrainingStatus status;
    private final int perCard;
    private final int validPerCard;
    private final int epochs;
    private final long seed;
    private final boolean useCaptures;

    public CardTrainingJob(AppPaths paths, CardCatalog cards, Backbone backbone, TrainingStatus status,
                           @Value("${app.synthetic.per-card:100}") int perCard,
                           @Value("${app.synthetic.valid-per-card:25}") int validPerCard,
                           @Value("${app.train.epochs:60}") int epochs,
                           @Value("${app.synthetic.seed:42}") long seed,
                           @Value("${app.train.use-captures:false}") boolean useCaptures) {
        this.paths = paths;
        this.cards = cards;
        this.backbone = backbone;
        this.status = status;
        this.perCard = perCard;
        this.validPerCard = validPerCard;
        this.epochs = epochs;
        this.seed = seed;
        this.useCaptures = useCaptures;
    }

    @Job(name = "Train Java-Dex card classifier", retries = 0)
    public void train(JobContext jobContext) throws Exception {
        List<Card> trainable = cards.all().stream()
                .filter(card -> cards.pngFor(card.id()).isPresent())
                .sorted(Comparator.comparing(Card::id))
                .toList();
        if (trainable.size() < 2) {
            throw new IllegalStateException("Need at least two cards with a PNG in " + paths.root());
        }
        int steps = trainable.size() + epochs;
        if (!status.start(steps)) {
            LOGGER.info("A training is already running, nothing to do");
            return;
        }
        JobDashboardProgressBar progress = jobContext.progressBar(steps);
        try {
            run(trainable, progress);
        } catch (Exception | Error e) {
            status.fail(String.valueOf(e.getMessage()));
            throw e;
        }
    }

    private void run(List<Card> trainable, JobDashboardProgressBar progress) throws Exception {
        long start = System.nanoTime();
        List<String> classes = trainable.stream().map(Card::id).toList();
        LOGGER.info("Training on {} cards: {} drawn pictures each, {} more for checking, {} epochs",
                classes.size(), perCard, validPerCard, epochs);

        // ---- 1. draw pictures, embed them
        status.phase("Drawing webcam pictures and running them through ResNet18");
        List<float[]> trainX = new ArrayList<>();
        List<Integer> trainY = new ArrayList<>();
        List<float[]> validX = new ArrayList<>();
        List<Integer> validY = new ArrayList<>();
        int realPhotos = 0;
        for (int label = 0; label < trainable.size(); label++) {
            String id = trainable.get(label).id();
            BufferedImage front = ImageIO.read(cards.pngFor(id).orElseThrow().toFile());
            long base = seed * 1_000_003L + label * 100_000L;

            addAll(trainX, trainY, drawAndEmbed(front, perCard, base), label);
            addAll(validX, validY, drawAndEmbed(front, validPerCard, base + 50_000L), label);
            List<float[]> real = embedRealPhotos(id);
            addAll(trainX, trainY, real, label);
            realPhotos += real.size();

            status.step();
            progress.incrementSucceeded();
            LOGGER.info("{} ({}/{})", id, label + 1, trainable.size());
        }
        long embedded = System.nanoTime();
        int pictures = trainX.size() + validX.size();
        LOGGER.info("Drew and embedded {} pictures ({} real photos among them) in {} s, {} ms per picture",
                pictures, realPhotos, seconds(embedded - start), (embedded - start) / 1_000_000 / pictures);

        // ---- 2. train the layer
        status.phase("Training the classifier layer");
        HeadTrainer.Result head = HeadTrainer.train(trainX, trainY, validX, validY, classes.size(), epochs,
                BATCH_SIZE, LEARNING_RATE, WEIGHT_DECAY, (epoch, total, accuracy) -> {
                    status.step();
                    progress.incrementSucceeded();
                    if (epoch % 5 == 0 || epoch == total) {
                        LOGGER.info("Epoch {}/{}: {} % on pictures it has not seen", epoch, total,
                                Math.round(accuracy * 1000) / 10.0);
                    }
                });
        long trained = System.nanoTime();
        LOGGER.info("Trained the layer in {} s", seconds(trained - embedded));

        // ---- 3. check, save
        status.phase("Saving the model");
        CardClassifier classifier = new CardClassifier(classes, head.weight(), head.bias());
        ConfusionMatrix matrix = evaluate(classifier, classes, validX, validY);
        long total = System.nanoTime() - start;
        save(classifier, matrix, trained - embedded, embedded - start, total, pictures);

        String summary = "%.1f %% of %d unseen pictures right, in %s s".formatted(
                matrix.overallAccuracy() * 100, validX.size(), seconds(total));
        LOGGER.info(summary);
        status.finish(summary);
    }

    private List<float[]> drawAndEmbed(BufferedImage front, int count, long baseSeed) throws Exception {
        // drawing is CPU work that parallelizes well, the ResNet itself is one batched pass
        List<BufferedImage> frames = IntStream.range(0, count).parallel()
                .mapToObj(i -> CardImageSynthesizer.render(front, new Random(baseSeed + i)))
                .toList();
        return backbone.embedAll(frames);
    }

    private List<float[]> embedRealPhotos(String cardId) throws Exception {
        Path dir = paths.captures().resolve(cardId);
        if (!useCaptures || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<BufferedImage> photos = new ArrayList<>();
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().toLowerCase().matches(".*\\.(jpe?g|png)")).toList()) {
                BufferedImage photo = ImageIO.read(file.toFile());
                if (photo != null) {
                    photos.add(photo);
                }
            }
        }
        return backbone.embedAll(photos);
    }

    private static void addAll(List<float[]> x, List<Integer> y, List<float[]> features, int label) {
        for (float[] row : features) {
            x.add(row);
            y.add(label);
        }
    }

    static ConfusionMatrix evaluate(CardClassifier classifier, List<String> classes, List<float[]> x,
                                    List<Integer> y) {
        List<ConfusionMatrix.Record> records = new ArrayList<>();
        for (int i = 0; i < x.size(); i++) {
            records.add(new ConfusionMatrix.Record(classes.get(y.get(i)), classifier.bestLabel(x.get(i))));
        }
        return new ConfusionMatrix(classes, records);
    }

    private void save(CardClassifier classifier, ConfusionMatrix matrix, long trainNanos, long embedNanos,
                      long totalNanos, int pictures) throws IOException {
        Path modelDir = paths.model();
        Files.createDirectories(modelDir);
        Files.deleteIfExists(modelDir.resolve("classes.txt"));
        classifier.writeHead(modelDir.resolve("head.jd"));
        Files.writeString(modelDir.resolve("evaluation.json"), report(matrix));

        Properties props = new Properties();
        props.setProperty("accuracy", Double.toString(matrix.overallAccuracy()));
        props.setProperty("pictures", Integer.toString(pictures));
        props.setProperty("epochs", Integer.toString(epochs));
        props.setProperty("embedSeconds", seconds(embedNanos));
        props.setProperty("trainSeconds", seconds(trainNanos));
        props.setProperty("totalSeconds", seconds(totalNanos));
        try (var out = Files.newOutputStream(modelDir.resolve("model.properties"))) {
            props.store(out, "Java-Dex card classifier");
        }
        // written last: the prediction service reloads the model when this file changes
        Files.write(modelDir.resolve("classes.txt"), classifier.classes(), StandardCharsets.UTF_8);
        LOGGER.info("Model saved to {}", modelDir.toAbsolutePath());
    }

    static String report(ConfusionMatrix matrix) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"overallAccuracy\": ").append(matrix.overallAccuracy()).append(",\n");
        json.append("  \"perClass\": [\n");
        var perClass = matrix.perClassAccuracy();
        json.append(String.join(",\n", matrix.classes().stream()
                .map(c -> "    {\"class\": \"%s\", \"accuracy\": %s}".formatted(c, perClass.get(c)))
                .toList()));
        json.append("\n  ],\n  \"confusion\": [\n");
        int[][] m = matrix.matrix();
        for (int i = 0; i < m.length; i++) {
            json.append("    ").append(java.util.Arrays.toString(m[i]));
            json.append(i < m.length - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        return json.toString();
    }

    private static String seconds(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1e9);
    }
}

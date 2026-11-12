package org.javadex.jobs;

import ai.djl.modality.cv.ImageFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.javadex.JavaDexApplicationConfiguration;
import org.javadex.ml.CardClassifier;
import org.javadex.ml.ConfusionMatrix;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class CardEvaluationJob {

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(CardEvaluationJob.class));

    private final JavaDexApplicationConfiguration config;
    private final ObjectMapper objectMapper;

    public CardEvaluationJob(JavaDexApplicationConfiguration config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Job(name = "Evaluate card classifier", retries = 0)
    public void evaluate(JobContext jobContext) throws Exception {
        Path modelDir = requireModel();
        List<Path> imagePaths = validImages();
        var progressBar = jobContext.progressBar(imagePaths.size());

        List<String> classes = Files.readAllLines(modelDir.resolve("classes.txt"));
        List<ConfusionMatrix.Record> records = new ArrayList<>();

        LOGGER.info("Evaluating on {} validation images", imagePaths.size());
        try (CardClassifier classifier = CardClassifier.load(modelDir)) {
            for (Path image : imagePaths) {
                String expected = image.getParent().getFileName().toString();
                String actual = CardClassifier.bestLabel(
                        classifier.classes(),
                        classifier.logits(ImageFactory.getInstance().fromFile(image)));
                records.add(new ConfusionMatrix.Record(expected, actual));
                progressBar.incrementSucceeded();
            }
        }

        ConfusionMatrix matrix = new ConfusionMatrix(classes, records);
        writeReport(matrix);
        LOGGER.info("Overall accuracy: {}", matrix.overallAccuracy());
    }

    Path requireModel() {
        Path modelDir = config.trainedModelPath();
        if (!Files.isDirectory(modelDir)) {
            throw new IllegalStateException("No trained model found in " + modelDir.toAbsolutePath() + " — run train first");
        }
        if (!Files.exists(modelDir.resolve("head.jd"))) {
            throw new IllegalStateException("No trained model found in " + modelDir.toAbsolutePath() + " — run train first");
        }
        return modelDir;
    }

    private List<Path> validImages() throws IOException {
        Path validDir = config.validationDatasetPath();
        if (!Files.isDirectory(validDir)) {
            throw new IllegalStateException("No validation dataset at " + validDir.toAbsolutePath() + " — run prepare first");
        }
        List<Path> result = new ArrayList<>();
        try (var dirs = Files.list(validDir)) {
            dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                try {
                    result.addAll(CardDataPreparationJob.sortedImages(dir));
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + dir, e);
                }
            });
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Validation dataset is empty — run prepare first");
        }
        return result;
    }

    private void writeReport(ConfusionMatrix matrix) throws IOException {
        Path report = config.trainedModelPath().resolve("evaluation.json");
        Files.writeString(report, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(EvaluationReport.from(matrix)));
        LOGGER.info("Evaluation report written to {}", report);
    }

    record EvaluationReport(double overallAccuracy, List<PerClass> perClass, List<List<Integer>> confusion) {

        public record PerClass(@JsonProperty("class") String className, double accuracy) {}

        public static EvaluationReport from(ConfusionMatrix matrix) {
            var accuracy = matrix.perClassAccuracy();
            List<PerClass> perClass = matrix.classes().stream()
                    .map(c -> new PerClass(c, accuracy.get(c)))
                    .toList();
            List<List<Integer>> confusion = Arrays.stream(matrix.matrix())
                    .map(row -> Arrays.stream(row).boxed().toList())
                    .toList();
            return new EvaluationReport(matrix.overallAccuracy(), perClass, confusion);
        }
    }
}

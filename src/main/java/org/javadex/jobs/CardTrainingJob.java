package org.javadex.jobs;

import ai.djl.Application;
import ai.djl.basicdataset.cv.classification.ImageFolder;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.RandomResizedCrop;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.LambdaBlock;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.Dataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.listener.TrainingListenerAdapter;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.training.util.ProgressBar;
import org.javadex.JavaDexApplicationConfiguration;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static ai.djl.Model.newInstance;
import static org.javadex.ml.CardClassifier.BACKBONE_LAYERS;
import static org.javadex.ml.CardClassifier.writeHead;

@Component
public class CardTrainingJob {

    public static final int IMAGE_SIZE = 224;
    static final int DEFAULT_EPOCHS = 60;
    static final int DEFAULT_BATCH_SIZE = 8;

    private static final Logger LOGGER = new JobRunrDashboardLogger(LoggerFactory.getLogger(CardTrainingJob.class));

    private final JavaDexApplicationConfiguration config;

    public CardTrainingJob(JavaDexApplicationConfiguration config) {
        this.config = config;
    }

    @Job(name = "Train Java-Dex card classifier", retries = 0)
    public void train(Integer epochs, Integer batchSize, JobContext jobContext) throws Exception {
        int actualEpochs = epochs != null ? epochs : DEFAULT_EPOCHS;
        int actualBatchSize = batchSize != null ? batchSize : DEFAULT_BATCH_SIZE;

        List<String> classes = validateClasses();
        var progressBar = jobContext.progressBar(actualEpochs + 2);

        Path trainDir = config.trainingDatasetPath();
        Path validDir = config.validationDatasetPath();
        Path modelDir = config.trainedModelPath();
        Files.createDirectories(modelDir);

        LOGGER.info("Training on {} classes, {} epochs, batch size {}", classes.size(), actualEpochs, actualBatchSize);

        Dataset trainSet = imageFolder(trainDir, actualBatchSize, true, true);
        Dataset validSet = imageFolder(validDir, actualBatchSize, false, false);
        progressBar.incrementSucceeded();

        Criteria<Image, Classifications> criteria = Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .setTypes(Image.class, Classifications.class)
                .optArtifactId("resnet")
                .optFilter("layers", BACKBONE_LAYERS)
                .optEngine("PyTorch")
                .optProgress(new ProgressBar())
                .build();

        try (ZooModel<Image, Classifications> pretrained = criteria.loadModel()) {
            Block backbone = pretrained.getBlock();
            backbone.freezeParameters(true);

            float accuracy;
            float loss;
            try (var model = newInstance("java-dex")) {
                SequentialBlock block = new SequentialBlock();
                block.add(backbone);
                // PyTorch marks frozen traced-module outputs as inference tensors;
                // the head needs a clone to run under autograd.
                block.add(new LambdaBlock(nd -> new NDList(nd.singletonOrThrow().duplicate())));
                block.add(Linear.builder().setUnits(classes.size()).build());
                model.setBlock(block);

                Optimizer optimizer = Optimizer.adam()
                        .optLearningRateTracker(Tracker.fixed(0.001f))
                        .build();

                List<TrainingListener> listeners = new ArrayList<>(List.of(TrainingListener.Defaults.logging()));
                listeners.add(epochProgress(progressBar::incrementSucceeded));

                DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                        .optOptimizer(optimizer)
                        .addEvaluator(new Accuracy())
                        .addTrainingListeners(listeners.toArray(new TrainingListener[0]));

                try (Trainer trainer = model.newTrainer(config)) {
                    trainer.initialize(new Shape(actualBatchSize, 3, IMAGE_SIZE, IMAGE_SIZE));
                    LOGGER.info("Training started (frozen ResNet{} backbone, trainable {}-class head)", BACKBONE_LAYERS, classes.size());
                    EasyTrain.fit(trainer, actualEpochs, trainSet, validSet);

                    var result = trainer.getTrainingResult();
                    accuracy = result.getValidateEvaluation("Accuracy");
                    loss = result.getValidateLoss();
                }
                LOGGER.info("Validation accuracy: {}, loss: {}", accuracy, loss);

                model.setProperty("accuracy", Float.toString(accuracy));
                model.setProperty("loss", Float.toString(loss));
                model.setProperty("imageSize", Integer.toString(IMAGE_SIZE));
                model.setProperty("epochs", Integer.toString(actualEpochs));
                saveHead(modelDir, block);

                var props = new Properties();
                props.setProperty("backbone", BACKBONE_LAYERS);
                props.setProperty("imageSize", Integer.toString(IMAGE_SIZE));
                props.setProperty("classes", Integer.toString(classes.size()));
                props.setProperty("accuracy", Float.toString(accuracy));
                try (var out = Files.newOutputStream(modelDir.resolve("model.properties"))) {
                    props.store(out, "Java-Dex card classifier");
                }
            }
        }

        Files.write(modelDir.resolve("classes.txt"), classes, StandardCharsets.UTF_8);
        progressBar.incrementSucceeded();
        LOGGER.info("Model saved to {}", modelDir.toAbsolutePath());
    }

    /**
     * The backbone is frozen ImageNet weights (identical to the zoo model, which the
     * reload path re-fetches), so only the trained linear head is persisted — in a
     * simple own binary format, because DJL parameter serialization cannot
     * round-trip a traced PtSymbolBlock + head combination.
     */
    private static void saveHead(Path modelDir, SequentialBlock block) throws IOException {
        var children = block.getChildren();
        var headParams = children.values().get(children.size() - 1).getParameters();
        float[] weightFlat = headParams.valueAt(0).getArray().toFloatArray();
        float[] bias = headParams.valueAt(1).getArray().toFloatArray();
        int units = bias.length;
        int features = weightFlat.length / units;
        float[][] weight = new float[units][features];
        for (int j = 0; j < units; j++) {
            System.arraycopy(weightFlat, j * features, weight[j], 0, features);
        }
        writeHead(modelDir.resolve("head.jd"), units, features, weight, bias);
    }

    List<String> validateClasses() throws IOException {
        List<String> trainClasses = classFolders(config.trainingDatasetPath());
        List<String> validClasses = classFolders(config.validationDatasetPath());

        if (trainClasses.size() < 2) {
            throw new IllegalStateException(
                    "Need at least two card classes to train, found " + trainClasses
                            + " in " + config.trainingDatasetPath().toAbsolutePath());
        }
        if (!trainClasses.equals(validClasses)) {
            throw new IllegalStateException(
                    "Training and validation classes differ — run prepare again. train=" + trainClasses
                            + " valid=" + validClasses);
        }
        return trainClasses;
    }

    private static List<String> classFolders(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException(dir.toAbsolutePath() + " does not exist — run prepare first");
        }
        try (var dirs = Files.list(dir)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private static Dataset imageFolder(Path dir, int batchSize, boolean augment, boolean shuffle) throws IOException {
        ImageFolder.Builder builder = ImageFolder.builder()
                .setRepositoryPath(dir)
                .optMaxDepth(1);
        if (augment) {
            builder.addTransform(new RandomResizedCrop(IMAGE_SIZE, IMAGE_SIZE, 0.6, 1.0, 3.0 / 4.0, 4.0 / 3.0))
                    .addTransform(new ToTensor())
                    .addTransform(CardTrainingJob::randomBrightness);
            return builder.setSampling(batchSize, shuffle).build();
        } else {
            builder.addTransform(new Resize(IMAGE_SIZE, IMAGE_SIZE));
        }
        return builder.addTransform(new ToTensor())
                .setSampling(batchSize, shuffle)
                .build();
    }

    /**
     * PyTorch engine does not implement RandomColorJitter; a brightness shift on
     * the normalized tensor is the augmentation that works on every engine.
     */
    static ai.djl.ndarray.NDArray randomBrightness(ai.djl.ndarray.NDArray tensor) {
        float shift = RANDOM.nextFloat() * 0.4f - 0.2f;
        return tensor.add(shift).clip(0, 1);
    }

    private static final java.util.random.RandomGenerator RANDOM = new java.util.Random();

    private static TrainingListener epochProgress(Runnable onEpochDone) {
        return new TrainingListenerAdapter() {
            @Override
            public void onEpoch(Trainer trainer) {
                onEpochDone.run();
            }
        };
    }
}

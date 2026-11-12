package org.javadex.ml;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The trained classifier, reloaded for inference.
 * <p>
 * The backbone is the frozen ImageNet ResNet from the PyTorch model zoo (identical
 * weights to training), so it is never persisted: only the trained linear head is,
 * in {@code head.jd} (own simple binary format — DJL's parameter serialization
 * cannot round-trip a traced PtSymbolBlock + head combination).
 * <p>
 * Head math runs in plain Java: logit_j = bias_j + weight_j · features.
 */
public class CardClassifier implements AutoCloseable {

    public static final String BACKBONE_LAYERS = "18";

    private final ZooModel<Image, float[]> backboneModel;
    private final Predictor<Image, float[]> backbonePredictor;
    private final List<String> classes;
    private final float[][] weight;
    private final float[] bias;

    private CardClassifier(ZooModel<Image, float[]> backboneModel, Predictor<Image, float[]> backbonePredictor,
                           List<String> classes, float[][] weight, float[] bias) {
        this.backboneModel = backboneModel;
        this.backbonePredictor = backbonePredictor;
        this.classes = classes;
        this.weight = weight;
        this.bias = bias;
    }

    public static CardClassifier load(Path modelDir) throws IOException, ModelNotFoundException, MalformedModelException {
        Path classesFile = modelDir.resolve("classes.txt");
        if (!Files.exists(classesFile)) {
            throw new IllegalStateException("No trained model found in " + modelDir.toAbsolutePath() + " — run train first");
        }
        List<String> classes = Files.readAllLines(classesFile, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        Criteria<Image, float[]> criteria = Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .setTypes(Image.class, float[].class)
                .optArtifactId("resnet")
                .optFilter("layers", BACKBONE_LAYERS)
                .optEngine("PyTorch")
                .optProgress(new ProgressBar())
                .optTranslator(new LogitsTranslator(imageSize(modelDir)))
                .build();

        ZooModel<Image, float[]> backboneModel = criteria.loadModel();
        Predictor<Image, float[]> predictor = backboneModel.newPredictor();
        float[][] weight = new float[classes.size()][];
        float[] bias = new float[classes.size()];
        readHead(modelDir.resolve("head.jd"), weight, bias);
        return new CardClassifier(backboneModel, predictor, classes, weight, bias);
    }

    public List<String> classes() {
        return classes;
    }

    public float[] logits(Image image) throws ai.djl.translate.TranslateException {
        float[] features = backbonePredictor.predict(image);
        float[] logits = new float[classes.size()];
        for (int j = 0; j < logits.length; j++) {
            float sum = bias[j];
            float[] row = weight[j];
            for (int i = 0; i < row.length; i++) {
                sum += row[i] * features[i];
            }
            logits[j] = sum;
        }
        return logits;
    }

    public static String bestLabel(List<String> classes, float[] logits) {
        int best = 0;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > logits[best]) {
                best = i;
            }
        }
        return classes.get(best);
    }

    public static void writeHead(Path file, int classes, int features, float[][] weight, float[] bias)
            throws IOException {
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(0x4a444831);
            out.writeInt(classes);
            out.writeInt(features);
            for (int j = 0; j < classes; j++) {
                for (float v : weight[j]) {
                    out.writeFloat(v);
                }
                out.writeFloat(bias[j]);
            }
        }
    }

    private static void readHead(Path file, float[][] weight, float[] bias) throws IOException {
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != 0x4a444831) {
                throw new IllegalStateException(file + " is not a Java-Dex head file");
            }
            int classes = in.readInt();
            int features = in.readInt();
            if (classes != weight.length) {
                throw new IllegalStateException("Head file has " + classes + " classes but classes.txt lists " + weight.length + " — train again");
            }
            for (int j = 0; j < classes; j++) {
                weight[j] = new float[features];
                for (int i = 0; i < features; i++) {
                    weight[j][i] = in.readFloat();
                }
                bias[j] = in.readFloat();
            }
        }
    }

    static int imageSize(Path modelDir) {
        Path propsFile = modelDir.resolve("model.properties");
        if (!Files.exists(propsFile)) {
            return 224;
        }
        try {
            var props = new java.util.Properties();
            props.load(Files.newInputStream(propsFile));
            return Integer.parseInt(props.getProperty("imageSize", "224"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        backbonePredictor.close();
        backboneModel.close();
    }
}

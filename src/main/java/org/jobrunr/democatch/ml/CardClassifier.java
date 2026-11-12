package org.jobrunr.democatch.ml;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The trained layer: logit_j = bias_j + weight_j · features, in plain Java. The ResNet in front of it is not stored
 * here, it is the same frozen pretrained network every time (see {@link Backbone}).
 *
 * <p>Stored in {@code head.jd}, a small binary format of its own, next to {@code classes.txt}.
 */
public class CardClassifier {

    private static final int MAGIC = 0x4a444831;

    private final List<String> classes;
    private final float[][] weight;
    private final float[] bias;

    public CardClassifier(List<String> classes, float[][] weight, float[] bias) {
        if (classes.size() != weight.length || classes.size() != bias.length) {
            throw new IllegalArgumentException(
                    classes.size() + " classes but " + weight.length + " weight rows and " + bias.length + " biases");
        }
        this.classes = List.copyOf(classes);
        this.weight = weight;
        this.bias = bias;
    }

    public static CardClassifier load(Path modelDir) throws IOException {
        Path classesFile = modelDir.resolve("classes.txt");
        if (!Files.exists(classesFile)) {
            throw new IllegalStateException(
                    "No trained model found in " + modelDir.toAbsolutePath() + " — train first");
        }
        List<String> classes = Files.readAllLines(classesFile, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        float[][] weight = new float[classes.size()][];
        float[] bias = new float[classes.size()];
        readHead(modelDir.resolve("head.jd"), weight, bias);
        return new CardClassifier(classes, weight, bias);
    }

    public List<String> classes() {
        return classes;
    }

    public float[] logits(float[] features) {
        float[] logits = new float[classes.size()];
        for (int j = 0; j < logits.length; j++) {
            float sum = bias[j];
            float[] row = weight[j];
            if (row.length != features.length) {
                throw new IllegalArgumentException(
                        "Model expects " + row.length + " features but got " + features.length);
            }
            for (int i = 0; i < row.length; i++) {
                sum += row[i] * features[i];
            }
            logits[j] = sum;
        }
        return logits;
    }

    public String bestLabel(float[] features) {
        float[] logits = logits(features);
        int best = 0;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > logits[best]) {
                best = i;
            }
        }
        return classes.get(best);
    }

    public void writeHead(Path file) throws IOException {
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(classes.size());
            out.writeInt(weight[0].length);
            for (int j = 0; j < classes.size(); j++) {
                for (float v : weight[j]) {
                    out.writeFloat(v);
                }
                out.writeFloat(bias[j]);
            }
        }
    }

    private static void readHead(Path file, float[][] weight, float[] bias) throws IOException {
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalStateException(file + " is not a Java-Dex head file");
            }
            int classes = in.readInt();
            int features = in.readInt();
            if (classes != weight.length) {
                throw new IllegalStateException(
                        "Head file has " + classes + " classes but classes.txt lists " + weight.length
                                + " — train again");
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
}

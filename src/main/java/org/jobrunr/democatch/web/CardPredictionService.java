package org.jobrunr.democatch.web;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.jobrunr.democatch.config.AppPaths;
import org.jobrunr.democatch.ml.Backbone;
import org.jobrunr.democatch.ml.CardClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ai.djl.translate.TranslateException;

@Service
public class CardPredictionService {

    public record Ranking(String label, double probability) {
    }

    /** {@code unsure}: the best guess is below the confidence limit, better not to name a card. */
    public record Prediction(String label, double probability, boolean unsure, List<Ranking> top3) {
    }

    private final AppPaths paths;
    private final Backbone backbone;
    private final double unsureBelow;
    private volatile CardClassifier classifier;
    private volatile long classesStamp = Long.MIN_VALUE;

    public CardPredictionService(AppPaths paths, Backbone backbone,
                                 @Value("${app.unsure-below:0.8}") double unsureBelow) {
        this.paths = paths;
        this.backbone = backbone;
        this.unsureBelow = unsureBelow;
    }

    public Prediction predict(byte[] jpeg) {
        CardClassifier current = classifierForCurrentModel();
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Not a readable image");
            }
            float[] probabilities = softmax(current.logits(backbone.embed(image)));
            List<String> classes = current.classes();

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < probabilities.length; i++) {
                order.add(i);
            }
            order.sort((a, b) -> Float.compare(probabilities[b], probabilities[a]));

            int best = order.get(0);
            List<Ranking> top3 = order.subList(0, Math.min(3, order.size())).stream()
                    .map(i -> new Ranking(classes.get(i), probabilities[i]))
                    .toList();
            return new Prediction(classes.get(best), probabilities[best], probabilities[best] < unsureBelow, top3);
        } catch (IOException | TranslateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot read image: " + e.getMessage());
        }
    }

    private CardClassifier classifierForCurrentModel() {
        Path classesFile = paths.model().resolve("classes.txt");
        if (!Files.exists(classesFile)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No model trained yet - train first");
        }
        try {
            long stamp = Files.getLastModifiedTime(classesFile).toMillis();
            if (classifier == null || stamp != classesStamp) {
                synchronized (this) {
                    if (classifier == null || stamp != classesStamp) {
                        classifier = CardClassifier.load(paths.model());
                        classesStamp = stamp;
                    }
                }
            }
            return classifier;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot load model: " + e.getMessage());
        }
    }

    static float[] softmax(float[] logits) {
        float max = logits[0];
        for (float v : logits) {
            max = Math.max(max, v);
        }
        double sum = 0;
        double[] exponentials = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exponentials[i] = Math.exp(logits[i] - max);
            sum += exponentials[i];
        }
        float[] result = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            result[i] = (float) (exponentials[i] / sum);
        }
        return result;
    }
}

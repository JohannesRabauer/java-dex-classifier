package org.javadex.ml;

import ai.djl.MalformedModelException;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import org.javadex.JavaDexApplicationConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class CardPredictionService {

    public record Ranking(String label, double probability) {
    }

    public record Prediction(String label, double probability, List<Ranking> top3) {
    }

    private final JavaDexApplicationConfiguration config;
    private volatile CardClassifier classifier;
    private volatile long classesStamp = Long.MIN_VALUE;

    public CardPredictionService(JavaDexApplicationConfiguration config) {
        this.config = config;
    }

    public Prediction predict(byte[] jpeg) {
        CardClassifier cardClassifier = reloadClassifierIfNecessary();
        try {
            Image image = ImageFactory.getInstance().fromInputStream(new ByteArrayInputStream(jpeg));
            float[] logits = cardClassifier.logits(image);
            float[] probabilities = softmax(logits);
            List<String> classes = cardClassifier.classes();

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < probabilities.length; i++) {
                order.add(i);
            }
            order.sort((a, b) -> Float.compare(probabilities[b], probabilities[a]));

            int best = order.getFirst();
            List<Ranking> top3 = order.subList(0, Math.min(3, order.size())).stream()
                    .map(i -> new Ranking(classes.get(i), probabilities[i]))
                    .toList();
            return new Prediction(classes.get(best), probabilities[best], top3);
        } catch (IOException | TranslateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Cannot read image: " + e.getMessage());
        }
    }

    private CardClassifier reloadClassifierIfNecessary() {
        Path classesFile = config.trainedModelPath().resolve("classes.txt");
        if (!Files.exists(classesFile)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No model trained yet - run prepare and train first");
        }
        try {
            long stamp = Files.getLastModifiedTime(classesFile).toMillis();
            if (classifier == null || stamp != classesStamp) {
                synchronized (this) {
                    if (classifier == null || stamp != classesStamp) {
                        CardClassifier old = classifier;
                        classifier = CardClassifier.load(config.trainedModelPath());
                        classesStamp = stamp;
                        if (old != null) {
                            old.close();
                        }
                    }
                }
            }
            return classifier;
        } catch (IOException | ModelNotFoundException | MalformedModelException e) {
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

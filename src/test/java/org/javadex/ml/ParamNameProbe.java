package org.javadex.ml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the reloaded classifier is the trained one: a held-out validation
 * image of jd07 must be named jd07. Runs against data/model from the e2e run;
 * skipped automatically when no trained model exists.
 */
@EnabledIf("org.javadex.ml.ParamNameProbe#trainedModelExists")
public class ParamNameProbe {

    static boolean trainedModelExists() {
        return Files.exists(Paths.get("data/model/head.jd"));
    }

    @Test
    void reloadedClassifierNamesValidationImage() throws Exception {
        Path validDir = Paths.get("data/dataset/valid/jd07");
        try (Stream<Path> files = Files.list(validDir)) {
            Path image = files.findFirst().orElseThrow();
            try (CardClassifier classifier = CardClassifier.load(Paths.get("data/model"))) {
                String label = CardClassifier.bestLabel(
                        classifier.classes(),
                        classifier.logits(ai.djl.modality.cv.ImageFactory.getInstance().fromFile(image)));
                System.out.println("PROBE predicted=" + label);
                assertEquals("jd07", label);
            }
        }
    }
}

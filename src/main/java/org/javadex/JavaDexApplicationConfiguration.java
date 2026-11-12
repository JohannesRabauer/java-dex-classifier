package org.javadex;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class JavaDexApplicationConfiguration {

    private final Path dataDir;

    public JavaDexApplicationConfiguration(@Value("${app.data-dir:data}") String dataDir) {
        this.dataDir = Path.of(dataDir);
    }

    public Path capturesPath() {
        return dataDir.resolve("captures");
    }

    public Path trainingDatasetPath() {
        return dataDir.resolve("dataset").resolve("train");
    }

    public Path validationDatasetPath() {
        return dataDir.resolve("dataset").resolve("valid");
    }

    public Path trainedModelPath() {
        return dataDir.resolve("model");
    }
}

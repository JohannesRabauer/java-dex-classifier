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
}

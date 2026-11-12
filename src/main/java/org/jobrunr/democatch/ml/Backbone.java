package org.jobrunr.democatch.ml;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Pipeline;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import jakarta.annotation.PreDestroy;

/**
 * ResNet18 pretrained on ImageNet, without its last layer, frozen. Turns a picture into 512 numbers (an embedding)
 * that describe what is in it. The weights never change: training only fits a small layer on top of these numbers,
 * and detection runs the same network in front of that layer.
 *
 * <p>Loaded on first use, so the application starts (and its tests run) without downloading PyTorch.
 */
@Component
public class Backbone implements AutoCloseable {

    public static final int IMAGE_SIZE = 224;
    public static final int FEATURES = 512;

    private static final int BATCH_SIZE = 32;
    // ImageNet statistics, the pretrained ResNet expects its input normalized with these
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private ZooModel<Image, float[]> model;
    private Predictor<Image, float[]> predictor;

    public synchronized float[] embed(BufferedImage image) throws TranslateException {
        return predictor().predict(ImageFactory.getInstance().fromImage(squash(image)));
    }

    /** Batched, which is a lot faster than one by one. Same order as the input. */
    public synchronized List<float[]> embedAll(List<BufferedImage> images) throws TranslateException {
        List<float[]> result = new ArrayList<>(images.size());
        for (int from = 0; from < images.size(); from += BATCH_SIZE) {
            List<Image> batch = images.subList(from, Math.min(images.size(), from + BATCH_SIZE)).stream()
                    .map(image -> ImageFactory.getInstance().fromImage(squash(image)))
                    .toList();
            result.addAll(predictor().batchPredict(batch));
        }
        return result;
    }

    /**
     * The same preparation for training and detection: the central 3:4 portrait window of the picture (the card guide
     * box on the page), scaled to what the ResNet takes. A picture that is 3:4 already is only scaled. Done here in
     * Java instead of by PyTorch ops per picture, which is several times faster.
     */
    static BufferedImage squash(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int cropW = w * 4 >= h * 3 ? h * 3 / 4 : w;
        int cropH = w * 4 >= h * 3 ? h : w * 4 / 3;
        BufferedImage current = source.getSubimage((w - cropW) / 2, (h - cropH) / 2, cropW, cropH);
        // halve while it is more than twice too big, a single big step would alias
        while (current.getWidth() >= 2 * IMAGE_SIZE) {
            current = scale(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return scale(current, IMAGE_SIZE, IMAGE_SIZE);
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return target;
    }

    private Predictor<Image, float[]> predictor() {
        if (predictor == null) {
            try {
                model = Criteria.builder()
                        .setTypes(Image.class, float[].class)
                        .optModelUrls("djl://ai.djl.pytorch/resnet18_embedding")
                        .optEngine("PyTorch")
                        .optTranslator(new EmbeddingTranslator())
                        .build()
                        .loadModel();
            } catch (IOException | ModelNotFoundException | MalformedModelException e) {
                throw new IllegalStateException("Cannot load the ResNet18 embedding model: " + e.getMessage(), e);
            }
            predictor = model.newPredictor();
        }
        return predictor;
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        if (predictor != null) {
            predictor.close();
            model.close();
            predictor = null;
            model = null;
        }
    }

    private static final class EmbeddingTranslator implements Translator<Image, float[]> {

        // the picture is already IMAGE_SIZE x IMAGE_SIZE, see squash()
        private final Pipeline pipeline = new Pipeline()
                .add(new ToTensor())
                .add(new Normalize(MEAN, STD));

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            return pipeline.transform(new NDList(input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR)));
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            // (512, 1, 1) once the batch dimension is gone
            return list.singletonOrThrow().toFloatArray();
        }
    }
}

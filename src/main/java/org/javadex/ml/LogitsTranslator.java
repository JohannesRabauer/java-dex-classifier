package org.javadex.ml;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDList;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * Feeds raw pixels to the frozen backbone and returns its raw output vector
 * (ImageNet logits used as features), without any zoo post-processing.
 */
public class LogitsTranslator implements Translator<Image, float[]> {

    private final int size;

    public LogitsTranslator(int size) {
        this.size = size;
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Image input) {
        return new Pipeline()
                .add(new Resize(size, size))
                .add(new ToTensor())
                .transform(new NDList(centerSquare(input).toNDArray(ctx.getNDManager(), Image.Flag.COLOR)));
    }

    /**
     * Webcam frames are wide while training images are square center-crops; a plain
     * resize would stretch the frame and shift the input distribution.
     */
    public static Image centerSquare(Image image) {
        var crop = CenterSquare.of(image.getWidth(), image.getHeight());
        return image.getSubImage(crop.x(), crop.y(), crop.size(), crop.size());
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        return list.get(0).toFloatArray();
    }
}

package org.javadex.ml;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.Trainer;
import ai.djl.training.loss.Loss;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spike: how a pretrained ResNet18 from the PyTorch model zoo is repurposed for
 * transfer learning. Finding: the block is a traced PtSymbolBlock and cannot be
 * unlayered (no removeLastBlock), so the recipe is: keep the pretrained block as a
 * frozen feature extractor and train a new head on top of its output.
 * Not gated by an annotation — runs only when explicitly filtered with
 * `--tests ResnetSpikeTest` (downloads ~100 MB on first use).
 */
class ResnetSpikeTest {

    static Criteria<Image, ai.djl.modality.Classifications> resnet18() {
        return Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .setTypes(Image.class, ai.djl.modality.Classifications.class)
                .optArtifactId("resnet")
                .optFilter("layers", "18")
                .optEngine("PyTorch")
                .optProgress(new ProgressBar())
                .build();
    }

    static SequentialBlock head(Block backbone, int classes) {
        SequentialBlock block = new SequentialBlock();
        block.add(backbone);
        block.add(Linear.builder().setUnits(classes).build());
        return block;
    }

    static final class RawImageTranslator implements Translator<Image, float[]> {
        private final int size;

        RawImageTranslator(int size) {
            this.size = size;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            return new ai.djl.translate.Pipeline()
                    .add(new ai.djl.modality.cv.transform.Resize(size, size))
                    .add(new ai.djl.modality.cv.transform.ToTensor())
                    .transform(new NDList(input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR)));
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            return list.get(0).toFloatArray();
        }
    }

    @Test
    void frozenBackbonePlusNewHeadForward() throws Exception {
        try (ZooModel<Image, ai.djl.modality.Classifications> pretrained = resnet18().loadModel()) {
            Block backbone = pretrained.getBlock();
            backbone.freezeParameters(true);

            try (var model = ai.djl.Model.newInstance("spike")) {
                model.setBlock(head(backbone, 20));
                try (Trainer trainer = model.newTrainer(new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss()))) {
                    trainer.initialize(new Shape(1, 3, 224, 224));
                    try (var out = trainer.forward(
                            new NDList(model.getNDManager().ones(new Shape(1, 3, 224, 224))))) {
                        assertEquals(new Shape(1, 20), out.singletonOrThrow().getShape(),
                                "frozen backbone + head must emit 20 logits");
                    }
                }
            }
        }
    }

    @Test
    void trainOneStepThenSaveAndReload() throws Exception {
        Path modelDir = Path.of("build", "spike-model");
        try (ZooModel<Image, ai.djl.modality.Classifications> pretrained = resnet18().loadModel()) {
            Block backbone = pretrained.getBlock();
            backbone.freezeParameters(true);

            try (var model = ai.djl.Model.newInstance("java-dex")) {
                model.setBlock(head(backbone, 20));
                model.setProperty("imageSize", "224");

                var manager = model.getNDManager();
                var dataset = new ai.djl.training.dataset.ArrayDataset.Builder()
                        .setData(manager.randomUniform(0f, 1f, new Shape(4, 3, 224, 224)))
                        .optLabels(manager.arange(0, 4, 1, ai.djl.ndarray.types.DataType.INT64))
                        .setSampling(4, false)
                        .build();

                var config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                        .addTrainingListeners(ai.djl.training.listener.TrainingListener.Defaults.logging());
                try (Trainer trainer = model.newTrainer(config)) {
                    trainer.initialize(new Shape(4, 3, 224, 224));
                    ai.djl.training.EasyTrain.fit(trainer, 1, dataset, null);
                }
                model.save(modelDir, "java-dex");
            }
        }

        Criteria<Image, ai.djl.modality.Classifications> reloadBackbone = resnet18();
        try (ZooModel<Image, ai.djl.modality.Classifications> pretrained = reloadBackbone.loadModel()) {
            Block backbone = pretrained.getBlock();
            backbone.freezeParameters(true);

            var model = ai.djl.Model.newInstance("java-dex", "PyTorch");
            model.setBlock(head(backbone, 20));
            model.load(modelDir, "java-dex");
            try (model; Predictor<Image, float[]> predictor = new Predictor<>(model, new RawImageTranslator(224), model.getNDManager().getDevice(), false)) {
                Image gray = ImageFactory.getInstance()
                        .fromNDArray(model.getNDManager().ones(new Shape(3, 224, 224)));
                float[] logits = predictor.predict(gray);
                assertEquals(20, logits.length, "reloaded model must emit 20 logits");
            }
        }
        try (var files = Files.list(modelDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("java-dex")
                            && p.getFileName().toString().endsWith(".params")),
                    "parameters must be saved next to the model");
        }
    }
}

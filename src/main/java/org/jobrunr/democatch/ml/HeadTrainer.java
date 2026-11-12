package org.jobrunr.democatch.ml;

import java.util.List;

import ai.djl.Model;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.core.Linear;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;

/**
 * Trains the one layer on top of the frozen ResNet: 512 embedding numbers in, one score per card out. It works on
 * stored embeddings, so an epoch is a matter of milliseconds.
 */
public final class HeadTrainer {

    public interface EpochListener {
        void onEpoch(int epoch, int epochs, double validationAccuracy);
    }

    public record Result(float[][] weight, float[] bias, double validationAccuracy) {
    }

    private HeadTrainer() {
    }

    public static Result train(List<float[]> trainX, List<Integer> trainY, List<float[]> validX, List<Integer> validY,
                               int classes, int epochs, int batchSize, float learningRate, float weightDecay,
                               EpochListener listener)
            throws Exception {
        try (NDManager manager = NDManager.newBaseManager();
             Model model = Model.newInstance("card-head")) {
            Block head = Linear.builder().setUnits(classes).build();
            model.setBlock(head);

            ArrayDataset train = dataset(manager, trainX, trainY, batchSize, true);
            ArrayDataset valid = dataset(manager, validX, validY, batchSize, false);

            DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                    .addEvaluator(new Accuracy())
                    .optOptimizer(Optimizer.adam().optLearningRateTracker(Tracker.fixed(learningRate))
                            .optWeightDecays(weightDecay).build())
                    // records loss and accuracy per epoch, without it getValidateEvaluation() returns null
                    .addTrainingListeners(TrainingListener.Defaults.basic());

            try (Trainer trainer = model.newTrainer(config)) {
                trainer.initialize(new Shape(batchSize, Backbone.FEATURES));
                double accuracy = 0;
                for (int epoch = 1; epoch <= epochs; epoch++) {
                    EasyTrain.fit(trainer, 1, train, valid);
                    accuracy = trainer.getTrainingResult().getValidateEvaluation("Accuracy");
                    listener.onEpoch(epoch, epochs, accuracy);
                }
                // read the weights while the trainer still owns them
                var parameters = head.getParameters();
                float[] weightFlat = parameters.get("weight").getArray().toFloatArray();
                float[] bias = parameters.get("bias").getArray().toFloatArray();
                return new Result(unflatten(weightFlat, bias.length), bias, accuracy);
            }
        }
    }

    static float[][] unflatten(float[] flat, int rows) {
        int columns = flat.length / rows;
        float[][] weight = new float[rows][columns];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(flat, row * columns, weight[row], 0, columns);
        }
        return weight;
    }

    private static ArrayDataset dataset(NDManager manager, List<float[]> x, List<Integer> y, int batchSize,
                                        boolean shuffle) {
        float[] labels = new float[y.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = y.get(i);
        }
        return new ArrayDataset.Builder()
                .setData(manager.create(x.toArray(float[][]::new)))
                .optLabels(manager.create(labels))
                .setSampling(batchSize, shuffle)
                .build();
    }
}

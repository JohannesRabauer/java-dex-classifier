package org.javadex.ml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfusionMatrix {

    public record Record(String expected, String actual) {
    }

    private final List<String> classes;
    private final int[][] matrix;

    public ConfusionMatrix(List<String> classes, List<Record> records) {
        this.classes = List.copyOf(classes);
        this.matrix = new int[classes.size()][classes.size()];
        for (Record record : records) {
            int row = classes.indexOf(record.expected());
            int column = classes.indexOf(record.actual());
            if (row < 0 || column < 0) {
                throw new IllegalArgumentException(
                        "Unknown class in record " + record + " (known: " + classes + ")");
            }
            matrix[row][column]++;
        }
    }

    public int[][] matrix() {
        int[][] copy = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i].clone();
        }
        return copy;
    }

    public double overallAccuracy() {
        long correct = 0;
        long total = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                total += matrix[i][j];
                if (i == j) {
                    correct += matrix[i][j];
                }
            }
        }
        return total == 0 ? 0.0 : (double) correct / total;
    }

    public Map<String, Double> perClassAccuracy() {
        Map<String, Double> result = new HashMap<>();
        for (int i = 0; i < classes.size(); i++) {
            long rowTotal = 0;
            for (int j = 0; j < matrix.length; j++) {
                rowTotal += matrix[i][j];
            }
            result.put(classes.get(i), rowTotal == 0 ? 0.0 : (double) matrix[i][i] / rowTotal);
        }
        return result;
    }

    public List<String> classes() {
        return classes;
    }
}

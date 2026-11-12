package org.javadex.ml;

/**
 * The centered largest square inside a width x height rectangle. Training images
 * and webcam frames must be cropped by the same rule, or the input
 * distributions drift — one place defines it.
 */
public record CenterSquare(int x, int y, int size) {

    public static CenterSquare of(int width, int height) {
        int size = Math.min(width, height);
        return new CenterSquare((width - size) / 2, (height - size) / 2, size);
    }
}

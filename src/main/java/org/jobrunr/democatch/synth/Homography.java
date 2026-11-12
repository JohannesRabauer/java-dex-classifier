package org.jobrunr.democatch.synth;

import java.awt.geom.Point2D;

/**
 * 3x3 projective transforms (row-major, 9 values) for drawing a flat card in perspective, which Java2D's affine
 * transforms cannot do.
 */
final class Homography {

    private Homography() {
    }

    /**
     * Maps the unit square (0,0) (1,0) (1,1) (0,1) onto the given quad, corners in the same order
     * (Heckbert, "Fundamentals of Texture Mapping", 1989).
     */
    static double[] squareToQuad(Point2D[] q) {
        double x0 = q[0].getX(), y0 = q[0].getY();
        double x1 = q[1].getX(), y1 = q[1].getY();
        double x2 = q[2].getX(), y2 = q[2].getY();
        double x3 = q[3].getX(), y3 = q[3].getY();
        double sx = x0 - x1 + x2 - x3;
        double sy = y0 - y1 + y2 - y3;
        double dx1 = x1 - x2, dx2 = x3 - x2;
        double dy1 = y1 - y2, dy2 = y3 - y2;
        double det = dx1 * dy2 - dx2 * dy1;
        double g = (sx * dy2 - dx2 * sy) / det;
        double h = (dx1 * sy - sx * dy1) / det;
        return new double[]{
                x1 - x0 + g * x1, x3 - x0 + h * x3, x0,
                y1 - y0 + g * y1, y3 - y0 + h * y3, y0,
                g, h, 1};
    }

    static double[] invert(double[] m) {
        double a = m[0], b = m[1], c = m[2];
        double d = m[3], e = m[4], f = m[5];
        double g = m[6], h = m[7], i = m[8];
        double A = e * i - f * h;
        double B = -(d * i - f * g);
        double C = d * h - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12) {
            throw new IllegalArgumentException("Degenerate quad");
        }
        return new double[]{
                A / det, -(b * i - c * h) / det, (b * f - c * e) / det,
                B / det, (a * i - c * g) / det, -(a * f - c * d) / det,
                C / det, -(a * h - b * g) / det, (a * e - b * d) / det};
    }

    static Point2D apply(double[] m, double x, double y) {
        double w = m[6] * x + m[7] * y + m[8];
        return new Point2D.Double((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w);
    }
}

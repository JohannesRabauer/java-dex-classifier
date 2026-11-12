package org.jobrunr.democatch.synth;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Turns one clean card picture into a fake webcam frame: the card held somewhere in a room, tilted, partly covered by
 * a thumb, under odd light, blurred, noisy and JPEG compressed. Thousands of these replace taking pictures by hand.
 *
 * <p>Never mirrors the card: a camera never shows the text the wrong way round, so the model shouldn't learn that.
 */
public final class CardImageSynthesizer {

    /**
     * A generated frame is a portrait window, 3:4, that the card nearly fills. Detection crops the same window out of
     * the camera picture (the guide box on the page), so the network sees mostly card and little of the room.
     * {@code Backbone.squash} and {@code detect.js} use the same 3:4.
     */
    public static final int FRAME_WIDTH = 240;
    public static final int FRAME_HEIGHT = 320;

    private static final Color[] SKIN = {
            new Color(255, 219, 172), new Color(241, 194, 125), new Color(224, 172, 105),
            new Color(198, 134, 66), new Color(141, 85, 36), new Color(92, 56, 30)};

    private CardImageSynthesizer() {
    }

    public static BufferedImage render(BufferedImage card, Random random) {
        return finish(compose(card, random), random);
    }

    /** Background, card, thumb and glare, before the camera effects. */
    static BufferedImage compose(BufferedImage card, Random random) {
        int width = FRAME_WIDTH;
        int height = FRAME_HEIGHT;
        BufferedImage frame = background(width, height, random);

        Point2D[] corners = cardCorners(card, width, height, random);
        Graphics2D g = graphics(frame);
        dropShadow(g, corners, random);
        g.dispose();
        drawWarped(card, frame, corners);

        g = graphics(frame);
        if (random.nextDouble() < 0.55) {
            thumb(g, corners, random);
        }
        if (random.nextDouble() < 0.35) {
            glare(g, corners, random);
        }
        g.dispose();
        return frame;
    }

    /** Camera effects: light, blur, resolution, noise and compression. */
    static BufferedImage finish(BufferedImage frame, Random random) {
        light(frame, random);
        if (random.nextDouble() < 0.45) {
            frame = blur(frame, 1 + random.nextInt(2));
        }
        if (random.nextDouble() < 0.25) {
            frame = motionBlur(frame, 3 + random.nextInt(5), random.nextDouble() * Math.PI);
        }
        if (random.nextDouble() < 0.4) {
            frame = lowResolution(frame, 0.3 + random.nextDouble() * 0.4);
        }
        noise(frame, random.nextDouble() * 12, random);
        return jpeg(frame, 0.35f + random.nextFloat() * 0.57f);
    }

    // ---------------------------------------------------------------- background

    static BufferedImage background(int width, int height, Random random) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        Color top = randomColor(random, 30, 230);
        Color bottom = randomColor(random, 20, 200);
        g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
        g.fillRect(0, 0, width, height);

        switch (random.nextInt(4)) {
            case 0 -> furniture(g, width, height, random);
            case 1 -> stripes(g, width, height, random);
            case 2 -> blobs(g, width, height, random);
            default -> {
                furniture(g, width, height, random);
                blobs(g, width, height, random);
            }
        }
        if (random.nextDouble() < 0.4) {
            person(g, width, height, random);
        }
        g.dispose();
        // a webcam focuses on the card, the room behind it is soft
        BufferedImage soft = blur(image, 1 + random.nextInt(3));
        noise(soft, 4 + random.nextDouble() * 8, random);
        return soft;
    }

    private static void furniture(Graphics2D g, int width, int height, Random random) {
        int count = 3 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            g.setColor(randomColor(random, 10, 240));
            int w = width / 8 + random.nextInt(width / 2);
            int h = height / 8 + random.nextInt(height / 2);
            g.fillRect(random.nextInt(width) - w / 2, random.nextInt(height) - h / 2, w, h);
        }
    }

    private static void stripes(Graphics2D g, int width, int height, Random random) {
        Color base = randomColor(random, 60, 200);
        int step = 6 + random.nextInt(30);
        boolean vertical = random.nextBoolean();
        for (int p = 0; p < (vertical ? width : height); p += step) {
            g.setColor(shade(base, 0.75 + random.nextDouble() * 0.5));
            if (vertical) {
                g.fillRect(p, 0, step, height);
            } else {
                g.fillRect(0, p, width, step);
            }
        }
    }

    private static void blobs(Graphics2D g, int width, int height, Random random) {
        int count = 4 + random.nextInt(10);
        for (int i = 0; i < count; i++) {
            g.setColor(randomColor(random, 0, 255));
            int r = 10 + random.nextInt(height / 2);
            g.fill(new Ellipse2D.Double(random.nextInt(width) - r, random.nextInt(height) - r, 2.0 * r, 2.0 * r * (0.5 + random.nextDouble())));
        }
    }

    /** A head and shoulders somewhere, as the person holding the card is usually in the picture. */
    private static void person(Graphics2D g, int width, int height, Random random) {
        Color skin = SKIN[random.nextInt(SKIN.length)];
        Color shirt = randomColor(random, 10, 230);
        double cx = width * (0.15 + random.nextDouble() * 0.7);
        double headR = height * (0.12 + random.nextDouble() * 0.12);
        double headY = height * (0.15 + random.nextDouble() * 0.3);
        g.setColor(shirt);
        g.fill(new Ellipse2D.Double(cx - headR * 2.6, headY + headR * 1.6, headR * 5.2, height));
        g.setColor(skin);
        g.fill(new Ellipse2D.Double(cx - headR, headY - headR * 1.2, headR * 2, headR * 2.5));
        g.setColor(randomColor(random, 0, 90));
        g.fill(new Ellipse2D.Double(cx - headR * 1.05, headY - headR * 1.35, headR * 2.1, headR * 1.2));
    }

    // ---------------------------------------------------------------- card

    /** Corners in order top-left, top-right, bottom-right, bottom-left. */
    static Point2D[] cardCorners(BufferedImage card, int width, int height, Random random) {
        double cardH = height * (0.72 + random.nextDouble() * 0.28);
        double cardW = cardH * card.getWidth() / card.getHeight();
        double cx = width * (0.44 + random.nextDouble() * 0.12);
        double cy = height * (0.46 + random.nextDouble() * 0.08);

        // perspective: one edge further away than the other, plus some wobble per corner
        double tiltX = (random.nextDouble() - 0.5) * 0.35;
        double tiltY = (random.nextDouble() - 0.5) * 0.35;
        double[][] base = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        double angle = Math.toRadians(random.nextGaussian() * 7);
        angle = Math.max(Math.toRadians(-25), Math.min(Math.toRadians(25), angle));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        Point2D[] corners = new Point2D[4];
        for (int i = 0; i < 4; i++) {
            double x = base[i][0] * cardW / 2;
            double y = base[i][1] * cardH / 2;
            x *= 1 + tiltY * base[i][1];
            y *= 1 + tiltX * base[i][0];
            x += random.nextGaussian() * cardW * 0.025;
            y += random.nextGaussian() * cardH * 0.025;
            corners[i] = new Point2D.Double(cx + x * cos - y * sin, cy + x * sin + y * cos);
        }
        return corners;
    }

    private static void dropShadow(Graphics2D g, Point2D[] corners, Random random) {
        double dx = random.nextGaussian() * 3;
        double dy = 2 + random.nextDouble() * 5;
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        path.moveTo(corners[0].getX() + dx, corners[0].getY() + dy);
        for (int i = 1; i < 4; i++) {
            path.lineTo(corners[i].getX() + dx, corners[i].getY() + dy);
        }
        path.closePath();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f + random.nextFloat() * 0.25f));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(path);
        g.draw(path);
        g.setComposite(AlphaComposite.SrcOver);
    }

    /**
     * Projects the card onto the four corners, sampling the source bilinearly through the inverse homography.
     * Works on int arrays: per-pixel getRGB/setRGB calls are what made this slow.
     */
    static void drawWarped(BufferedImage card, BufferedImage frame, Point2D[] corners) {
        int cw = card.getWidth();
        int ch = card.getHeight();
        double[] inverse = Homography.invert(Homography.squareToQuad(corners));

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Point2D c : corners) {
            minX = Math.min(minX, c.getX());
            minY = Math.min(minY, c.getY());
            maxX = Math.max(maxX, c.getX());
            maxY = Math.max(maxY, c.getY());
        }
        int x0 = Math.max(0, (int) Math.floor(minX));
        int y0 = Math.max(0, (int) Math.floor(minY));
        int x1 = Math.min(frame.getWidth() - 1, (int) Math.ceil(maxX));
        int y1 = Math.min(frame.getHeight() - 1, (int) Math.ceil(maxY));
        if (x1 < x0 || y1 < y0) {
            return;
        }
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;
        int[] source = card.getRGB(0, 0, cw, ch, null, 0, cw);
        int[] target = frame.getRGB(x0, y0, w, h, null, 0, w);
        boolean alpha = card.getColorModel().hasAlpha();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double px = x0 + x + 0.5;
                double py = y0 + y + 0.5;
                double q = inverse[6] * px + inverse[7] * py + inverse[8];
                double u = (inverse[0] * px + inverse[1] * py + inverse[2]) / q;
                double v = (inverse[3] * px + inverse[4] * py + inverse[5]) / q;
                if (u < 0 || u >= 1 || v < 0 || v >= 1) {
                    continue;
                }
                int argb = bilinear(source, cw, ch, u * cw - 0.5, v * ch - 0.5);
                int a = alpha ? (argb >>> 24) : 255;
                if (a == 0) {
                    continue;
                }
                target[y * w + x] = a == 255 ? argb : blend(target[y * w + x], argb, a / 255.0);
            }
        }
        frame.setRGB(x0, y0, w, h, target, 0, w);
    }

    private static int bilinear(int[] pixels, int w, int h, double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        int xa = clamp(xi, 0, w - 1);
        int xb = clamp(xi + 1, 0, w - 1);
        int ya = clamp(yi, 0, h - 1) * w;
        int yb = clamp(yi + 1, 0, h - 1) * w;
        int c00 = pixels[ya + xa];
        int c10 = pixels[ya + xb];
        int c01 = pixels[yb + xa];
        int c11 = pixels[yb + xb];
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            double top = ((c00 >>> shift) & 0xFF) * (1 - fx) + ((c10 >>> shift) & 0xFF) * fx;
            double bottom = ((c01 >>> shift) & 0xFF) * (1 - fx) + ((c11 >>> shift) & 0xFF) * fx;
            result |= clamp((int) Math.round(top * (1 - fy) + bottom * fy), 0, 255) << shift;
        }
        return result;
    }

    /** A thumb gripping one of the card's long edges. */
    private static void thumb(Graphics2D g, Point2D[] corners, Random random) {
        // left edge (0 -> 3) or right edge (1 -> 2), sometimes the bottom edge (3 -> 2)
        int edge = random.nextInt(5);
        Point2D a, b, opposite;
        if (edge < 2) {
            a = corners[0]; b = corners[3]; opposite = corners[1];
        } else if (edge < 4) {
            a = corners[1]; b = corners[2]; opposite = corners[0];
        } else {
            a = corners[3]; b = corners[2]; opposite = corners[0];
        }
        double t = 0.2 + random.nextDouble() * 0.6;
        double ex = a.getX() + (b.getX() - a.getX()) * t;
        double ey = a.getY() + (b.getY() - a.getY()) * t;
        double inX = opposite.getX() - a.getX();
        double inY = opposite.getY() - a.getY();
        double len = Math.hypot(inX, inY);
        double cardWidth = len;
        double thumbW = cardWidth * (0.18 + random.nextDouble() * 0.12);
        double thumbL = thumbW * (1.6 + random.nextDouble() * 0.8);
        double reach = thumbL * (0.3 + random.nextDouble() * 0.35);

        AffineTransform saved = g.getTransform();
        g.translate(ex, ey);
        g.rotate(Math.atan2(inY, inX));
        Color skin = shade(SKIN[random.nextInt(SKIN.length)], 0.8 + random.nextDouble() * 0.35);
        g.setPaint(new GradientPaint(0, (float) -thumbW / 2, shade(skin, 1.1), 0, (float) thumbW / 2, shade(skin, 0.75)));
        g.fill(new RoundRectangle2D.Double(reach - thumbL, -thumbW / 2, thumbL, thumbW, thumbW, thumbW));
        // the nail
        g.setColor(shade(skin, 1.18));
        g.fill(new RoundRectangle2D.Double(reach - thumbW * 0.75, -thumbW * 0.3, thumbW * 0.6, thumbW * 0.6, thumbW * 0.4, thumbW * 0.4));
        g.setTransform(saved);
    }

    /** A reflection of a lamp or window on the glossy card. */
    private static void glare(Graphics2D g, Point2D[] corners, Random random) {
        double u = 0.15 + random.nextDouble() * 0.7;
        double v = 0.15 + random.nextDouble() * 0.7;
        double top = lerp(corners[0].getX(), corners[1].getX(), u);
        double bottom = lerp(corners[3].getX(), corners[2].getX(), u);
        double x = lerp(top, bottom, v);
        double y = lerp(lerp(corners[0].getY(), corners[1].getY(), u), lerp(corners[3].getY(), corners[2].getY(), u), v);
        double cardH = corners[0].distance(corners[3]);
        float radius = (float) (cardH * (0.08 + random.nextDouble() * 0.25));
        float strength = 0.4f + random.nextFloat() * 0.55f;
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.rotate(random.nextDouble() * Math.PI);
        g.scale(1, 0.4 + random.nextDouble() * 0.6);
        g.setPaint(new RadialGradientPaint(0, 0, radius, new float[]{0f, 1f},
                new Color[]{new Color(1f, 1f, 1f, strength), new Color(1f, 1f, 1f, 0f)}));
        g.fill(new Ellipse2D.Double(-radius, -radius, 2.0 * radius, 2.0 * radius));
        g.setTransform(saved);
    }

    // ---------------------------------------------------------------- camera

    /** White balance, brightness, contrast, gamma and light falling in from one side. */
    static void light(BufferedImage image, Random random) {
        double[] gain = {0.8 + random.nextDouble() * 0.4, 0.85 + random.nextDouble() * 0.3, 0.8 + random.nextDouble() * 0.4};
        double brightness = 0.55 + random.nextDouble() * 0.8;
        double contrast = 0.65 + random.nextDouble() * 0.6;
        double gamma = 0.75 + random.nextDouble() * 0.6;
        boolean sideLight = random.nextDouble() < 0.5;
        double lightAngle = random.nextDouble() * 2 * Math.PI;
        double lightStrength = 0.15 + random.nextDouble() * 0.35;
        int w = image.getWidth();
        int h = image.getHeight();
        double lx = Math.cos(lightAngle);
        double ly = Math.sin(lightAngle);

        // gamma, contrast, brightness and white balance only depend on the pixel value: look them up instead of
        // calling pow() three times per pixel
        float[][] lut = new float[3][256];
        for (int v = 0; v < 256; v++) {
            double value = Math.pow(v / 255.0, gamma);
            value = (value - 0.5) * contrast + 0.5;
            for (int c = 0; c < 3; c++) {
                lut[c][v] = (float) (value * brightness * gain[c] * 255);
            }
        }

        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double side = 1;
                if (sideLight) {
                    double pos = ((x / (double) w - 0.5) * lx + (y / (double) h - 0.5) * ly) * 2; // -1 .. 1
                    side = 1 + pos * lightStrength;
                }
                int p = pixels[y * w + x];
                int r = clamp((int) (lut[0][(p >> 16) & 0xFF] * side + 0.5), 0, 255);
                int g = clamp((int) (lut[1][(p >> 8) & 0xFF] * side + 0.5), 0, 255);
                int b = clamp((int) (lut[2][p & 0xFF] * side + 0.5), 0, 255);
                pixels[y * w + x] = (r << 16) | (g << 8) | b;
            }
        }
        image.setRGB(0, 0, w, h, pixels, 0, w);
    }

    static BufferedImage blur(BufferedImage image, int radius) {
        int size = radius * 2 + 1;
        float[] line = new float[size];
        java.util.Arrays.fill(line, 1f / size);
        BufferedImage horizontal = convolve(image, new Kernel(size, 1, line));
        return convolve(horizontal, new Kernel(1, size, line));
    }

    static BufferedImage motionBlur(BufferedImage image, int length, double angle) {
        int size = length | 1;
        float[] kernel = new float[size * size];
        int count = 0;
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        for (int i = -size / 2; i <= size / 2; i++) {
            int x = (int) Math.round(size / 2 + i * c);
            int y = (int) Math.round(size / 2 + i * s);
            if (kernel[y * size + x] == 0) {
                kernel[y * size + x] = 1;
                count++;
            }
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= count;
        }
        return convolve(image, new Kernel(size, size, kernel));
    }

    private static BufferedImage convolve(BufferedImage image, Kernel kernel) {
        // pad by clamping the edges, ConvolveOp would otherwise leave a black or unprocessed border
        int px = kernel.getWidth() / 2;
        int py = kernel.getHeight() / 2;
        BufferedImage padded = new BufferedImage(image.getWidth() + 2 * px, image.getHeight() + 2 * py, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = padded.createGraphics();
        g.drawImage(image, px, py, null);
        g.drawImage(image, 0, py, px, py + image.getHeight(), 0, 0, 1, image.getHeight(), null);
        g.drawImage(image, px + image.getWidth(), py, padded.getWidth(), py + image.getHeight(), image.getWidth() - 1, 0, image.getWidth(), image.getHeight(), null);
        g.drawImage(padded, 0, 0, padded.getWidth(), py, 0, py, padded.getWidth(), py + 1, null);
        g.drawImage(padded, 0, py + image.getHeight(), padded.getWidth(), padded.getHeight(), 0, py + image.getHeight() - 1, padded.getWidth(), py + image.getHeight(), null);
        g.dispose();
        BufferedImage result = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(padded, null);
        return result.getSubimage(px, py, image.getWidth(), image.getHeight());
    }

    static BufferedImage lowResolution(BufferedImage image, double factor) {
        int w = Math.max(32, (int) (image.getWidth() * factor));
        int h = Math.max(24, (int) (image.getHeight() * factor));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(small);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        BufferedImage big = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        g = graphics(big);
        g.drawImage(small, 0, 0, image.getWidth(), image.getHeight(), null);
        g.dispose();
        return big;
    }

    static void noise(BufferedImage image, double sigma, Random random) {
        if (sigma < 0.5) {
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int luma = (int) Math.round(random.nextGaussian() * sigma);
            int r = clamp(((p >> 16) & 0xFF) + luma + (int) (random.nextGaussian() * sigma * 0.3), 0, 255);
            int gr = clamp(((p >> 8) & 0xFF) + luma + (int) (random.nextGaussian() * sigma * 0.3), 0, 255);
            int b = clamp((p & 0xFF) + luma + (int) (random.nextGaussian() * sigma * 0.3), 0, 255);
            pixels[i] = (r << 16) | (gr << 8) | b;
        }
        image.setRGB(0, 0, w, h, pixels, 0, w);
    }

    static BufferedImage jpeg(BufferedImage image, float quality) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var out = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(out);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(rgb(image), null, null), param);
            } finally {
                writer.dispose();
            }
            return ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static BufferedImage rgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static int blend(int under, int over, double alpha) {
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            double v = ((under >>> shift) & 0xFF) * (1 - alpha) + ((over >>> shift) & 0xFF) * alpha;
            result |= clamp((int) Math.round(v), 0, 255) << shift;
        }
        return result;
    }

    private static Color randomColor(Random random, int min, int max) {
        int span = max - min;
        return new Color(min + random.nextInt(span), min + random.nextInt(span), min + random.nextInt(span));
    }

    private static Color shade(Color color, double factor) {
        return new Color(clamp((int) (color.getRed() * factor), 0, 255),
                clamp((int) (color.getGreen() * factor), 0, 255),
                clamp((int) (color.getBlue() * factor), 0, 255));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

package org.javadex.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Generates synthetic webcam-like captures from the card PNGs so the whole
 * pipeline (prepare → train → evaluate → predict) can be verified without a
 * webcam. Deterministic seed for reproducible datasets.
 */
public final class SyntheticCaptureGenerator {

    static void main(String[] args) throws IOException {
        Path cardsDir = Path.of(arg(args, "--cards-dir", "cards"));
        Path outDir = Path.of(arg(args, "--out-dir", "data/captures"));
        int perCard = Integer.parseInt(arg(args, "--per-card", "10"));
        long seed = Long.parseLong(arg(args, "--seed", "42"));

        List<Path> cards;
        try (var pngs = Files.list(cardsDir.resolve("png"))) {
            cards = pngs.filter(p -> p.getFileName().toString().matches("jd\\d+\\.png"))
                    .sorted()
                    .toList();
        }

        Random random = new Random(seed);
        for (Path card : cards) {
            String cardId = card.getFileName().toString().replace(".png", "");
            Path target = outDir.resolve(cardId);
            Files.createDirectories(target);
            BufferedImage front = ImageIO.read(card.toFile());
            for (int i = 0; i < perCard; i++) {
                BufferedImage capture = synthesize(front, random);
                ImageIO.write(capture, "jpg", target.resolve(String.format("syn_%02d.jpg", i)).toFile());
            }
            System.out.println(cardId + ": " + perCard + " captures");
        }
        System.out.println("Done: " + cards.size() + " cards x " + perCard + " captures in " + outDir.toAbsolutePath());
    }

    static BufferedImage synthesize(BufferedImage card, Random random) {
        int bgW = 1280;
        int bgH = 960;
        BufferedImage background = new BufferedImage(bgW, bgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = background.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color base = new Color(random.nextInt(220), random.nextInt(220), random.nextInt(220));
        graphics.setColor(base);
        graphics.fillRect(0, 0, bgW, bgH);

        double scale = 0.7 + random.nextDouble() * 0.25;
        double cardW = card.getWidth() * scale;
        double cardH = card.getHeight() * scale;
        double angle = Math.toRadians(random.nextDouble() * 12 - 6);
        int x = (int) (bgW / 2.0 - cardW / 2.0 + random.nextInt(80) - 40);
        int y = (int) (bgH / 2.0 - cardH / 2.0 + random.nextInt(60) - 30);

        AffineTransform transform = new AffineTransform();
        transform.rotate(angle, x + cardW / 2.0, y + cardH / 2.0);
        transform.translate(x, y);
        transform.scale(scale, scale);
        graphics.drawImage(card, transform, null);
        graphics.dispose();

        applyBrightnessShift(background, random.nextInt(30) - 15);
        return background;
    }

    private static void applyBrightnessShift(BufferedImage image, int shift) {
        if (shift == 0) {
            return;
        }
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int r = clamp((rgb >> 16) & 0xFF + shift);
                int g = clamp((rgb >> 8) & 0xFF + shift);
                int b = clamp((rgb & 0xFF) + shift);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String arg(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}

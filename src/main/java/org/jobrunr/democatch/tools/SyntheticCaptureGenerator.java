package org.jobrunr.democatch.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.jobrunr.democatch.synth.CardImageSynthesizer;

/**
 * Writes a few synthetic training pictures per card to disk, to look at what the model learns from without starting
 * the app. Training itself renders its pictures in memory, in the JobRunr jobs.
 *
 * <pre>./gradlew generateSyntheticCaptures -PperCard=5</pre>
 */
public final class SyntheticCaptureGenerator {

    public static void main(String[] args) throws IOException {
        Path cardsDir = Path.of(arg(args, "--cards-dir", "cards"));
        Path outDir = Path.of(arg(args, "--out-dir", "data/preview"));
        int perCard = Integer.parseInt(arg(args, "--per-card", "5"));
        long seed = Long.parseLong(arg(args, "--seed", "42"));

        List<Path> cards;
        try (var pngs = Files.list(cardsDir.resolve("png"))) {
            cards = pngs.filter(p -> p.getFileName().toString().matches("jd\\d+\\.png"))
                    .sorted()
                    .toList();
        }

        Random random = new Random(seed);
        long start = System.nanoTime();
        for (Path card : cards) {
            String cardId = card.getFileName().toString().replace(".png", "");
            Path target = outDir.resolve(cardId);
            Files.createDirectories(target);
            BufferedImage front = ImageIO.read(card.toFile());
            for (int i = 0; i < perCard; i++) {
                BufferedImage capture = CardImageSynthesizer.render(front, random);
                ImageIO.write(capture, "jpg", target.resolve(String.format("syn_%02d.jpg", i)).toFile());
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Done: %d cards x %d pictures in %s (%d ms, %.1f ms per picture)%n",
                cards.size(), perCard, outDir.toAbsolutePath(), millis, millis / (double) (cards.size() * perCard));
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

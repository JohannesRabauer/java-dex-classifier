package org.jobrunr.democatch.synth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import org.junit.jupiter.api.Test;

class CardImageSynthesizerTest {

    /** Stand-in for a card: red left half, blue right half, so orientation can be told. */
    private static BufferedImage card() {
        BufferedImage card = new BufferedImage(118, 182, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = card.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 59, 182);
        g.setColor(Color.BLUE);
        g.fillRect(59, 0, 59, 182);
        g.dispose();
        return card;
    }

    @Test
    void rendersAPortraitFrame() {
        BufferedImage frame = CardImageSynthesizer.render(card(), new Random(1));
        assertEquals(CardImageSynthesizer.FRAME_WIDTH, frame.getWidth());
        assertEquals(CardImageSynthesizer.FRAME_HEIGHT, frame.getHeight());
    }

    @Test
    void sameSeedGivesTheSamePicture() {
        BufferedImage a = CardImageSynthesizer.render(card(), new Random(7));
        BufferedImage b = CardImageSynthesizer.render(card(), new Random(7));
        assertArrayEquals(pixels(a), pixels(b));
    }

    @Test
    void differentSeedsGiveDifferentPictures() {
        BufferedImage a = CardImageSynthesizer.render(card(), new Random(7));
        BufferedImage b = CardImageSynthesizer.render(card(), new Random(8));
        assertNotEquals(a.getWidth() * 1000 + a.getHeight() + java.util.Arrays.hashCode(pixels(a)),
                b.getWidth() * 1000 + b.getHeight() + java.util.Arrays.hashCode(pixels(b)));
    }

    @Test
    void warpDrawsTheCardInsideItsCornersAndKeepsItsOrientation() {
        BufferedImage frame = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Point2D[] corners = {new Point2D.Double(50, 40), new Point2D.Double(150, 50),
                new Point2D.Double(140, 160), new Point2D.Double(60, 150)};
        CardImageSynthesizer.drawWarped(card(), frame, corners);

        // the card's left (red) half is on the left, the right (blue) half on the right
        Color left = new Color(frame.getRGB(75, 100));
        Color right = new Color(frame.getRGB(125, 100));
        assertTrue(left.getRed() > 200 && left.getBlue() < 60, "left is " + left);
        assertTrue(right.getBlue() > 200 && right.getRed() < 60, "right is " + right);
        // outside the quad nothing was drawn
        assertEquals(0, frame.getRGB(10, 10) & 0xFFFFFF);
        assertEquals(0, frame.getRGB(190, 190) & 0xFFFFFF);
    }

    @Test
    void homographyMapsTheUnitSquareOntoTheCornersAndBack() {
        Point2D[] corners = {new Point2D.Double(10, 20), new Point2D.Double(210, 40),
                new Point2D.Double(190, 300), new Point2D.Double(30, 260)};
        double[] h = Homography.squareToQuad(corners);
        double[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        double[] inverse = Homography.invert(h);
        for (int i = 0; i < 4; i++) {
            Point2D mapped = Homography.apply(h, square[i][0], square[i][1]);
            assertEquals(corners[i].getX(), mapped.getX(), 1e-6);
            assertEquals(corners[i].getY(), mapped.getY(), 1e-6);
            Point2D back = Homography.apply(inverse, mapped.getX(), mapped.getY());
            assertEquals(square[i][0], back.getX(), 1e-6);
            assertEquals(square[i][1], back.getY(), 1e-6);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}

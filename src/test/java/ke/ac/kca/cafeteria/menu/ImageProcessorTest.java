package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    /** A gradient with a red square in the top-left corner, so rotation can be seen. */
    static BufferedImage picture(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.ORANGE, width, height, Color.DARK_GRAY));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 10, height / 10);
        g.dispose();
        return image;
    }

    static byte[] encode(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** Inserts an EXIF block with the given orientation straight after the JPEG start marker. */
    static byte[] withOrientation(byte[] jpeg, int orientation) {
        int[] exif = {
            0xFF, 0xE1, 0x00, 0x22,
            'E', 'x', 'i', 'f', 0, 0,
            'M', 'M', 0x00, 0x2A, 0, 0, 0, 8,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0, 0, 0, 1, 0x00, orientation, 0, 0,
            0, 0, 0, 0};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]);
        out.write(jpeg[1]);
        for (int b : exif) {
            out.write(b);
        }
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static boolean isRed(int rgb) {
        return ((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) < 90;
    }

    @Test
    void largePhotoIsShrunkToTwelveHundredPixelsOnItsLongSide() throws Exception {
        ImageProcessor.Processed result = ImageProcessor.process(encode(picture(3000, 2000), "jpg"));
        assertEquals(1200, result.width());
        assertEquals(800, result.height());
        assertEquals(1200, decode(result.jpeg()).getWidth());
    }

    @Test
    void smallPhotoIsNotEnlarged() throws Exception {
        ImageProcessor.Processed result = ImageProcessor.process(encode(picture(400, 300), "png"));
        assertEquals(400, result.width());
        assertEquals(300, result.height());
    }

    @Test
    void transparentPngBecomesWhiteJpeg() throws Exception {
        BufferedImage transparent = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        ImageProcessor.Processed result = ImageProcessor.process(encode(transparent, "png"));
        int pixel = decode(result.jpeg()).getRGB(25, 25) & 0xFFFFFF;
        assertTrue((pixel & 0xFF) > 240 && ((pixel >> 8) & 0xFF) > 240 && ((pixel >> 16) & 0xFF) > 240);
    }

    @Test
    void sidewaysPhoneJpegIsTurnedUpright() throws Exception {
        byte[] jpeg = encode(picture(300, 200), "jpg");

        ImageProcessor.Processed clockwise = ImageProcessor.process(withOrientation(jpeg, 6));
        assertEquals(200, clockwise.width());
        assertEquals(300, clockwise.height());
        assertTrue(isRed(decode(clockwise.jpeg()).getRGB(190, 10)), "marker should move to the top right");

        ImageProcessor.Processed counter = ImageProcessor.process(withOrientation(jpeg, 8));
        assertEquals(200, counter.width());
        assertTrue(isRed(decode(counter.jpeg()).getRGB(10, 290)), "marker should move to the bottom left");

        ImageProcessor.Processed upsideDown = ImageProcessor.process(withOrientation(jpeg, 3));
        assertEquals(300, upsideDown.width());
        assertTrue(isRed(decode(upsideDown.jpeg()).getRGB(290, 190)), "marker should move to the bottom right");
    }

    @Test
    void uprightPhotoKeepsItsOrientation() throws Exception {
        ImageProcessor.Processed result = ImageProcessor.process(encode(picture(300, 200), "jpg"));
        assertEquals(300, result.width());
        assertTrue(isRed(decode(result.jpeg()).getRGB(10, 10)));
    }

    @Test
    void notAnImageIsRejected() {
        assertThrows(MenuException.class, () -> ImageProcessor.process("hello, not an image".getBytes()));
        assertThrows(MenuException.class, () -> ImageProcessor.process(new byte[0]));
        assertThrows(MenuException.class, () -> ImageProcessor.process(null));
    }

    @Test
    void otherImageFormatsAreRejectedEvenIfTheyAreRealImages() throws Exception {
        assertThrows(MenuException.class, () -> ImageProcessor.process(encode(picture(100, 100), "gif")));
        assertThrows(MenuException.class, () -> ImageProcessor.process(encode(picture(100, 100), "bmp")));
    }

    @Test
    void uploadsOverThreeMegabytesAreRejected() {
        MenuException e = assertThrows(MenuException.class,
                () -> ImageProcessor.process(new byte[ImageProcessor.MAX_UPLOAD_BYTES + 1]));
        assertTrue(e.getMessage().contains("3 MB"));
    }

    @Test
    void outputIsAlwaysAJpegOfReasonableSize() throws Exception {
        ImageProcessor.Processed result = ImageProcessor.process(encode(picture(2400, 1600), "png"));
        assertNotNull(decode(result.jpeg()));
        assertTrue(result.jpeg().length < 500_000);
        // a JPEG starts with FF D8
        assertEquals((byte) 0xFF, result.jpeg()[0]);
        assertEquals((byte) 0xD8, result.jpeg()[1]);
    }
}

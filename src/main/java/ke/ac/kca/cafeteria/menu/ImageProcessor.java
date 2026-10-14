package ke.ac.kca.cafeteria.menu;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Turns an uploaded photo into a small, safe JPEG for menu cards.
 * The real file type is detected from the file's content, never from its name or declared type.
 * Only JPEG and PNG are accepted. Large photos are shrunk, sideways phone photos are turned upright,
 * and metadata such as GPS location is dropped because a new JPEG is written from the pixels alone.
 */
public final class ImageProcessor {

    public static final int MAX_UPLOAD_BYTES = 3 * 1024 * 1024;
    static final int MAX_SOURCE_SIDE = 6000;
    static final long MAX_SOURCE_PIXELS = 25_000_000L;
    static final int OUTPUT_MAX_SIDE = 1200;
    static final float QUALITY = 0.82f;

    private ImageProcessor() {
    }

    public record Processed(byte[] jpeg, int width, int height) {
    }

    public static Processed process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new MenuException("Choose a photo to upload.");
        }
        if (input.length > MAX_UPLOAD_BYTES) {
            throw new MenuException("The photo must be 3 MB or smaller.");
        }
        try {
            BufferedImage source = read(input);
            int orientation = ExifOrientation.read(input);
            BufferedImage upright = orient(source, orientation);
            BufferedImage scaled = scaleDown(upright, OUTPUT_MAX_SIDE);
            return new Processed(writeJpeg(scaled), scaled.getWidth(), scaled.getHeight());
        } catch (MenuException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new MenuException("That file could not be read as a JPEG or PNG photo.");
        }
    }

    private static BufferedImage read(byte[] input) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (stream == null) {
                throw new MenuException("That file could not be read as a JPEG or PNG photo.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new MenuException("Only JPEG and PNG photos are accepted.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new MenuException("Only JPEG and PNG photos are accepted.");
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE
                        || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new MenuException("The photo is too large in pixels. Use one under 6000 by 6000.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Rotates for EXIF orientations 3 (upside down), 6 (needs 90 degrees clockwise) and 8 (counter-clockwise). */
    private static BufferedImage orient(BufferedImage source, int orientation) {
        int w = source.getWidth();
        int h = source.getHeight();
        AffineTransform transform = new AffineTransform();
        int newWidth = w;
        int newHeight = h;
        switch (orientation) {
            case 3 -> {
                transform.translate(w, h);
                transform.rotate(Math.PI);
            }
            case 6 -> {
                newWidth = h;
                newHeight = w;
                transform.translate(h, 0);
                transform.rotate(Math.PI / 2);
            }
            case 8 -> {
                newWidth = h;
                newHeight = w;
                transform.translate(0, w);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
                return source;
            }
        }
        BufferedImage out = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source, transform, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Shrinks so the longer side is at most maxSide, halving repeatedly first for a smoother result. */
    private static BufferedImage scaleDown(BufferedImage source, int maxSide) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        BufferedImage current = toRgb(source);
        if (longest <= maxSide) {
            return current;
        }
        double factor = (double) maxSide / longest;
        int targetW = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * factor));
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        return resize(source, source.getWidth(), source.getHeight());
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);                       // transparent PNG areas become white
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}

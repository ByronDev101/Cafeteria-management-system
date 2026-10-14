package ke.ac.kca.cafeteria.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExifOrientationTest {

    private static byte[] jpeg() throws Exception {
        return ImageProcessorTest.encode(ImageProcessorTest.picture(60, 40), "jpg");
    }

    @Test
    void jpegWithoutExifIsUpright() throws Exception {
        assertEquals(1, ExifOrientation.read(jpeg()));
    }

    @Test
    void readsOrientationValues() throws Exception {
        assertEquals(6, ExifOrientation.read(ImageProcessorTest.withOrientation(jpeg(), 6)));
        assertEquals(8, ExifOrientation.read(ImageProcessorTest.withOrientation(jpeg(), 8)));
        assertEquals(3, ExifOrientation.read(ImageProcessorTest.withOrientation(jpeg(), 3)));
    }

    @Test
    void invalidOrientationValueIsTreatedAsUpright() throws Exception {
        assertEquals(1, ExifOrientation.read(ImageProcessorTest.withOrientation(jpeg(), 9)));
        assertEquals(1, ExifOrientation.read(ImageProcessorTest.withOrientation(jpeg(), 0)));
    }

    @Test
    void garbageAndTinyInputNeverThrow() {
        assertEquals(1, ExifOrientation.read(new byte[0]));
        assertEquals(1, ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8}));
        assertEquals(1, ExifOrientation.read("not a jpeg at all".getBytes()));
        assertEquals(1, ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x7F, 0x7F, 1, 2, 3}));
    }
}

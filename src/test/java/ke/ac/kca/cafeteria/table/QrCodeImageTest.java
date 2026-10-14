package ke.ac.kca.cafeteria.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QrCodeImageTest {

    @Test
    void theDrawnQrCodeReadsBackAsTheSameAddress() throws Exception {
        String text = "http://192.168.1.20:8080/t/" + TableToken.generate();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(QrCodeImage.png(text, 300)));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        Result result = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))));

        assertEquals(text, result.getText());
    }

    @Test
    void dataUriIsAPngReadyForAnImageTag() {
        String uri = QrCodeImage.pngDataUri("http://host/t/" + TableToken.generate(), 200);
        assertTrue(uri.startsWith("data:image/png;base64,"));
        assertTrue(uri.length() > 200);
    }
}

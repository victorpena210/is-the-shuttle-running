package technology.renatus.routepulse.driving.service.rider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import technology.renatus.routepulse.driving.service.PassengerRideService;

/** Builds the public passenger-confirmation QR code for a ride page. */
@Service
public class PassengerRideQrService {

    private static final int QR_SIZE = 640;
    private static final int QR_MARGIN = 2;

    private final PassengerRideService passengerRideService;
    private final String applicationBaseUrl;

    public PassengerRideQrService(
            PassengerRideService passengerRideService,
            @Value("${app.base-url}") String applicationBaseUrl) {
        this.passengerRideService = passengerRideService;
        this.applicationBaseUrl = applicationBaseUrl.replaceAll("/+$", "");
    }

    public byte[] generatePng(String organizationSlug)
            throws WriterException, IOException {
        passengerRideService.requirePublicRidePage(organizationSlug);
        BitMatrix matrix = new MultiFormatWriter().encode(
                buildRideUrl(organizationSlug),
                BarcodeFormat.QR_CODE,
                QR_SIZE,
                QR_SIZE,
                Map.of(EncodeHintType.MARGIN, QR_MARGIN));
        return renderPng(matrix);
    }

    private String buildRideUrl(String organizationSlug) {
        return UriComponentsBuilder
                .fromUriString(applicationBaseUrl)
                .path("/ride/")
                .pathSegment(organizationSlug)
                .fragment("confirm-ride")
                .build()
                .encode()
                .toUriString();
    }

    private byte[] renderPng(BitMatrix matrix) throws IOException {
        BufferedImage image = new BufferedImage(
                matrix.getWidth(),
                matrix.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }
}

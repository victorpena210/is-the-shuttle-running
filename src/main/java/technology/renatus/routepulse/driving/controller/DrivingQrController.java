package technology.renatus.routepulse.driving.controller;

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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import technology.renatus.routepulse.driving.service.DrivingQrService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DrivingQrController {

    private final CurrentOrganizationService
            currentOrganizationService;

    private final DrivingQrService
            drivingQrService;

    private final String applicationBaseUrl;

    public DrivingQrController(
            CurrentOrganizationService
                    currentOrganizationService,
            DrivingQrService
                    drivingQrService,
            @Value("${app.base-url}")
            String applicationBaseUrl) {

        this.currentOrganizationService =
                currentOrganizationService;

        this.drivingQrService =
                drivingQrService;

        this.applicationBaseUrl =
                applicationBaseUrl
                        .replaceAll(
                                "/+$",
                                ""
                        );
    }

    @GetMapping("/driving-qr")
    public String showDrivingQr(
            Authentication authentication,
            Model model) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        model.addAttribute(
                "organizationName",
                organization.getDisplayName()
        );

        model.addAttribute(
                "nfcUrl",
                buildCheckpointUrl(
                        "/driver/tap",
                        organization.getDrivingQrCode()
                )
        );

        return "manager/driving-qr";
    }

    @PostMapping("/driving-qr/regenerate")
    public String regenerateDrivingQr(
            Authentication authentication,
            RedirectAttributes
                    redirectAttributes) {

        drivingQrService
                .regenerateDrivingQrCode(
                        authentication
                );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "A new shuttle checkpoint code was generated. "
                        + "The previous QR code and NFC URL are now invalid. "
                        + "Print the new QR and reprogram the NFC tag with the new URL."
        );

        return "redirect:/driving-qr";
    }

    @GetMapping(
            value = "/driving-qr/image",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @ResponseBody
    public ResponseEntity<byte[]>
            getDrivingQrImage(
                    Authentication authentication)
                    throws WriterException,
                    IOException {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        String scanUrl = buildCheckpointUrl(
                "/driver/scan",
                organization.getDrivingQrCode()
        );

        BitMatrix matrix =
                new MultiFormatWriter()
                        .encode(
                                scanUrl,
                                BarcodeFormat.QR_CODE,
                                640,
                                640,
                                Map.of(
                                        EncodeHintType.MARGIN,
                                        2
                                )
                        );

        BufferedImage image =
                new BufferedImage(
                        matrix.getWidth(),
                        matrix.getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );

        for (int y = 0;
                y < matrix.getHeight();
                y++) {

            for (int x = 0;
                    x < matrix.getWidth();
                    x++) {

                image.setRGB(
                        x,
                        y,
                        matrix.get(
                                x,
                                y
                        )
                                ? 0x000000
                                : 0xFFFFFF
                );
            }
        }

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ImageIO.write(
                image,
                "PNG",
                output
        );

        return ResponseEntity
                .ok()
                .cacheControl(
                        CacheControl.noStore()
                )
                .contentType(
                        MediaType.IMAGE_PNG
                )
                .body(
                        output.toByteArray()
                );
    }

    private String buildCheckpointUrl(
            String path,
            String code) {

        return UriComponentsBuilder
                .fromUriString(applicationBaseUrl)
                .path(path)
                .queryParam("code", code)
                .build()
                .encode()
                .toUriString();
    }
}

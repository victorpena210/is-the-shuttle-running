package technology.renatus.routepulse.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import technology.renatus.routepulse.service.PassengerRideService;
import technology.renatus.routepulse.service.PassengerRideService.ConfirmationResult;
import technology.renatus.routepulse.service.PassengerRideService.PassengerRideNotFoundException;
import technology.renatus.routepulse.service.PassengerRideService.PassengerRideState;
import technology.renatus.routepulse.service.RiderStatusService;
import technology.renatus.routepulse.service.RiderStatusService.RiderStatus;
import technology.renatus.routepulse.service.RiderStatusService.RiderStatusNotFoundException;

@Controller
public class RiderStatusController {

    private static final String DEVICE_COOKIE =
            "rp_rider_device";

    private final RiderStatusService
            riderStatusService;

    private final PassengerRideService
            passengerRideService;

    private final String applicationBaseUrl;

    public RiderStatusController(
            RiderStatusService riderStatusService,
            PassengerRideService passengerRideService,
            @Value("${app.base-url}")
            String applicationBaseUrl) {

        this.riderStatusService =
                riderStatusService;

        this.passengerRideService =
                passengerRideService;

        this.applicationBaseUrl =
                applicationBaseUrl
                        .replaceAll(
                                "/+$",
                                ""
                        );
    }

    @GetMapping("/ride/{organizationSlug}")
    public String showRiderStatus(
            @PathVariable String organizationSlug,
            @CookieValue(
                    name = DEVICE_COOKIE,
                    required = false)
            String existingDeviceToken,
            @RequestParam(
                    name = "ride",
                    required = false)
            String rideResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        setNoCacheHeaders(
                response
        );

        try {

            RiderStatus status =
                    riderStatusService
                            .getStatus(
                                    organizationSlug
                            );

            model.addAttribute(
                    "status",
                    status
            );

            model.addAttribute(
                    "organizationSlug",
                    organizationSlug
            );

            model.addAttribute(
                    "rideResult",
                    rideResult
            );

            if (status.running()) {

                String deviceToken =
                        ensureDeviceToken(
                                existingDeviceToken,
                                request,
                                response
                        );

                PassengerRideState passengerRide =
                        passengerRideService
                                .getState(
                                        organizationSlug,
                                        deviceToken
                                );

                model.addAttribute(
                        "passengerRide",
                        passengerRide
                );
            }

            return "rider-status";

        } catch (
                RiderStatusNotFoundException
                        | PassengerRideNotFoundException
                                exception) {

            throw notFound(
                    exception
            );
        }
    }

    @PostMapping("/ride/{organizationSlug}/confirm")
    public String confirmPassengerRide(
            @PathVariable String organizationSlug,
            @CookieValue(
                    name = DEVICE_COOKIE,
                    required = false)
            String existingDeviceToken,
            HttpServletRequest request,
            HttpServletResponse response) {

        String deviceToken =
                ensureDeviceToken(
                        existingDeviceToken,
                        request,
                        response
                );

        try {

            ConfirmationResult result =
                    passengerRideService
                            .confirmRide(
                                    organizationSlug,
                                    deviceToken
                            );

            String resultValue =
                    switch (result) {

                        case RECORDED ->
                                "confirmed";

                        case ALREADY_CONFIRMED ->
                                "duplicate";

                        case NO_ACTIVE_SHUTTLE ->
                                "stopped";
                    };

            return "redirect:/ride/"
                    + organizationSlug
                    + "?ride="
                    + resultValue
                    + "#confirm-ride";

        } catch (PassengerRideNotFoundException exception) {

            throw notFound(
                    exception
            );
        }
    }

    @GetMapping(
            value = "/ride/{organizationSlug}/passenger-qr.png",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @ResponseBody
    public ResponseEntity<byte[]> getPassengerQrImage(
            @PathVariable String organizationSlug)
            throws WriterException,
            IOException {

        try {

            passengerRideService
                    .requirePublicRidePage(
                            organizationSlug
                    );

            String rideUrl =
                    UriComponentsBuilder
                            .fromUriString(
                                    applicationBaseUrl
                            )
                            .path(
                                    "/ride/"
                            )
                            .pathSegment(
                                    organizationSlug
                            )
                            .fragment(
                                    "confirm-ride"
                            )
                            .build()
                            .encode()
                            .toUriString();

            BitMatrix matrix =
                    new MultiFormatWriter()
                            .encode(
                                    rideUrl,
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

        } catch (PassengerRideNotFoundException exception) {

            throw notFound(
                    exception
            );
        }
    }

    private String ensureDeviceToken(
            String existingDeviceToken,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (isUsableDeviceToken(
                existingDeviceToken)) {

            return existingDeviceToken;
        }

        String newDeviceToken =
                UUID.randomUUID()
                        .toString();

        boolean secureCookie =
                request.isSecure()
                        || applicationBaseUrl
                                .startsWith(
                                        "https://"
                                );

        ResponseCookie cookie =
                ResponseCookie
                        .from(
                                DEVICE_COOKIE,
                                newDeviceToken
                        )
                        .httpOnly(
                                true
                        )
                        .secure(
                                secureCookie
                        )
                        .sameSite(
                                "Lax"
                        )
                        .path(
                                "/ride"
                        )
                        .maxAge(
                                Duration.ofDays(
                                        180
                                )
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        return newDeviceToken;
    }

    private boolean isUsableDeviceToken(
            String deviceToken) {

        return deviceToken != null
                && !deviceToken.isBlank()
                && deviceToken.length() <= 200;
    }

    private void setNoCacheHeaders(
            HttpServletResponse response) {

        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0"
        );

        response.setHeader(
                "Pragma",
                "no-cache"
        );
    }

    private ResponseStatusException notFound(
            RuntimeException exception) {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }
}
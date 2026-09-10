package technology.renatus.routepulse.driving.controller;

import com.google.zxing.WriterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import technology.renatus.routepulse.driving.service.PassengerRideService;
import technology.renatus.routepulse.driving.service.PassengerRideService.ConfirmationResult;
import technology.renatus.routepulse.driving.service.PassengerRideService.PassengerRideNotFoundException;
import technology.renatus.routepulse.driving.service.PassengerRideService.PassengerRideState;
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.driving.service.RiderStatusService.RiderStatus;
import technology.renatus.routepulse.driving.service.RiderStatusService.RiderStatusNotFoundException;
import technology.renatus.routepulse.driving.service.rider.PassengerRideQrService;
import technology.renatus.routepulse.driving.service.rider.RiderDeviceCookieService;

@Controller
public class RiderStatusController {

    private final RiderStatusService riderStatusService;
    private final PassengerRideService passengerRideService;
    private final RiderDeviceCookieService riderDeviceCookieService;
    private final PassengerRideQrService passengerRideQrService;

    public RiderStatusController(
            RiderStatusService riderStatusService,
            PassengerRideService passengerRideService,
            RiderDeviceCookieService riderDeviceCookieService,
            PassengerRideQrService passengerRideQrService) {
        this.riderStatusService = riderStatusService;
        this.passengerRideService = passengerRideService;
        this.riderDeviceCookieService = riderDeviceCookieService;
        this.passengerRideQrService = passengerRideQrService;
    }

    @GetMapping("/ride/{organizationSlug}")
    public String showRiderStatus(
            @PathVariable String organizationSlug,
            @CookieValue(
                    name = RiderDeviceCookieService.DEVICE_COOKIE,
                    required = false) String existingDeviceToken,
            @RequestParam(name = "ride", required = false) String rideResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        setNoCacheHeaders(response);

        try {
            RiderStatus status = riderStatusService.getStatus(organizationSlug);
            model.addAttribute("status", status);
            model.addAttribute("organizationSlug", organizationSlug);
            model.addAttribute("rideResult", rideResult);

            if (status.confirmationAvailable()) {
                String deviceToken = riderDeviceCookieService.ensureDeviceToken(
                        existingDeviceToken,
                        request,
                        response);
                PassengerRideState passengerRide = passengerRideService.getState(
                        organizationSlug,
                        deviceToken);
                model.addAttribute("passengerRide", passengerRide);
            }

            return "public/rider-status";
        } catch (RiderStatusNotFoundException | PassengerRideNotFoundException exception) {
            throw notFound(exception);
        }
    }

    @PostMapping("/ride/{organizationSlug}/confirm")
    public String confirmPassengerRide(
            @PathVariable String organizationSlug,
            @CookieValue(
                    name = RiderDeviceCookieService.DEVICE_COOKIE,
                    required = false) String existingDeviceToken,
            HttpServletRequest request,
            HttpServletResponse response) {

        String deviceToken = riderDeviceCookieService.ensureDeviceToken(
                existingDeviceToken,
                request,
                response);

        try {
            ConfirmationResult result = passengerRideService.confirmRide(
                    organizationSlug,
                    deviceToken);
            return "redirect:/ride/"
                    + organizationSlug
                    + "?ride="
                    + rideResultValue(result)
                    + "#confirm-ride";
        } catch (PassengerRideNotFoundException exception) {
            throw notFound(exception);
        }
    }

    @GetMapping(
            value = "/ride/{organizationSlug}/passenger-qr.png",
            produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getPassengerQrImage(
            @PathVariable String organizationSlug)
            throws WriterException, IOException {
        try {
            return ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(passengerRideQrService.generatePng(organizationSlug));
        } catch (PassengerRideNotFoundException exception) {
            throw notFound(exception);
        }
    }

    private String rideResultValue(ConfirmationResult result) {
        return switch (result) {
            case RECORDED -> "confirmed";
            case ALREADY_CONFIRMED -> "duplicate";
            case NO_ACTIVE_SHUTTLE -> "stopped";
        };
    }

    private void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }

    private ResponseStatusException notFound(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}

package technology.renatus.routepulse.driving.service.rider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Owns the anonymous rider device token used to rate-limit confirmations. */
@Service
public class RiderDeviceCookieService {

    public static final String DEVICE_COOKIE = "rp_rider_device";

    private final boolean secureByDefault;

    public RiderDeviceCookieService(@Value("${app.base-url}") String applicationBaseUrl) {
        this.secureByDefault = applicationBaseUrl
                .replaceAll("/+$", "")
                .startsWith("https://");
    }

    public String ensureDeviceToken(
            String existingDeviceToken,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (isUsableDeviceToken(existingDeviceToken)) {
            return existingDeviceToken;
        }

        String newDeviceToken = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie
                .from(DEVICE_COOKIE, newDeviceToken)
                .httpOnly(true)
                .secure(request.isSecure() || secureByDefault)
                .sameSite("Lax")
                .path("/ride")
                .maxAge(Duration.ofDays(180))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return newDeviceToken;
    }

    private boolean isUsableDeviceToken(String deviceToken) {
        return deviceToken != null
                && !deviceToken.isBlank()
                && deviceToken.length() <= 200;
    }
}

package technology.renatus.routepulse.driver.calendar;

import java.net.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class UkgCalendarClient {
    public static final String HOST = "cedarfair.prd.mykronos.com";
    public String normalize(String input) {
        try {
            var uri = new URI(input == null ? "" : input.strip());
            if (!Set.of("webcal", "https").contains(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getRawPath().matches("/api/calendar_sync/[A-Za-z0-9_-]{20,512}")) throw new IllegalArgumentException();
            return "https://" + HOST + uri.getRawPath();
        } catch (Exception exception) { throw new IllegalArgumentException("Paste the complete UKG subscription link from Cedar Fair's Sync My Schedule screen."); }
    }
    public List<UkgCalendarParser.Shift> fetch(String link) {
        return UkgCalendarParser.parse(fetchBytes(link));
    }
    public List<UkgCalendarParser.DetailedShift> fetchDetails(String link) {
        return UkgCalendarParser.parseDetails(fetchBytes(link));
    }
    private byte[] fetchBytes(String link) {
        String normalized = normalize(link); HttpURLConnection connection = null;
        try {
            // The destination and path are fixed; user-entered hosts, ports and redirects are never followed.
            connection = (HttpURLConnection) new URI(normalized).toURL().openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "text/calendar"); connection.setUseCaches(false);
            int status = connection.getResponseCode();
            if (status != 200) throw new IllegalStateException("UKG could not refresh this subscription. Check that the link is still active or reconnect it.");
            byte[] bytes;
            try (var stream = connection.getInputStream()) { bytes = stream.readNBytes(1_048_577); }
            return bytes;
        } catch (IllegalArgumentException | IllegalStateException exception) { throw exception;
        } catch (Exception exception) {
            // Do not retain the exception cause: HTTP errors can contain the private subscription token.
            throw new IllegalStateException("UKG is unavailable right now. Try refreshing again shortly.");
        } finally { if (connection != null) connection.disconnect(); }
    }
}

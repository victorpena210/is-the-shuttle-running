package technology.renatus.routepulse.shared.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Formats persisted UTC instants in the RoutePulse operations timezone. */
@Component("timestampDisplay")
public class TimestampDisplay {

    private final ZoneId operationsZone;

    public TimestampDisplay(
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public String format(Instant instant, String pattern) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.US)
                .withZone(operationsZone)
                .format(instant);
    }
}

package technology.renatus.routepulse.driver.service.portal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;

/** Protects scheduled drivers from accidentally starting a QR session too early. */
@Component
public class DriverQrStartPolicy {

    private static final DateTimeFormatter DISPLAY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public void requireStartAllowed(
            Driver scanningDriver,
            DriverAssignment assignment,
            LocalDateTime now) {

        Objects.requireNonNull(scanningDriver, "Scanning driver is required.");
        Objects.requireNonNull(assignment, "Assignment is required.");
        Objects.requireNonNull(now, "Scan time is required.");

        LocalTime scheduledStartTime = assignment.getScheduledStartTime();

        /*
         * Missing scheduled times are handled by DriverScanAssignmentSelector.
         * Keeping this guard focused on the early-start rule avoids duplicating
         * that validation and its manager-facing message.
         */
        if (scheduledStartTime == null) {
            return;
        }

        /*
         * A different eligible driver is an intentional fill-in/coverage scan.
         * The existing QR confirmation screen already makes that override
         * explicit and preserves the original scheduled driver.
         *
         * Manager starts use DrivingActionService rather than this driver QR
         * policy, so managers retain their existing override path as well.
         */
        if (!sameDriver(assignment.getScheduledDriver(), scanningDriver)) {
            return;
        }

        if (!now.toLocalTime().isBefore(scheduledStartTime)) {
            return;
        }

        throw new IllegalStateException(
                "Your driving assignment begins at "
                        + scheduledStartTime.format(DISPLAY_TIME_FORMAT)
                        + ". Start when you clock in."
        );
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null
                && second != null
                && Objects.equals(first.getId(), second.getId());
    }
}

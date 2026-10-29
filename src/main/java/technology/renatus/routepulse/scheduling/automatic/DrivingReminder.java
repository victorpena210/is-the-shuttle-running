package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Immutable email snapshot; only this driver's saved assignments are included. */
public record DrivingReminder(Long deliveryId, String recipient, String driverName, String organizationName,
        LocalDateTime workStart, LocalDateTime workEnd, ZoneId zone, List<Assignment> assignments) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    public record Assignment(LocalDateTime start, LocalDateTime end, String label, boolean estimatedEnd) { }

    public DrivingReminder { assignments = List.copyOf(assignments); }

    static boolean isDue(LocalDateTime clockIn, ZoneId zone, Instant now) {
        var start = clockIn.atZone(zone).toInstant();
        return !now.isBefore(start.minusSeconds(300)) && now.isBefore(start);
    }

    public String subject() { return "Your driving assignments — " + workStart.toLocalDate().format(DATE); }

    public String body(String baseUrl) {
        var body = new StringBuilder("Hello " + driverName + ",\n\n")
                .append("Here are your driving assignments for ").append(workStart.toLocalDate().format(DATE)).append(".\n")
                .append("Team: ").append(organizationName).append("\n")
                .append("Scheduled work shift: ").append(range(workStart, workEnd)).append("\n")
                .append("All times are ").append(zone.getId()).append(".\n\n");
        for (var assignment : assignments) {
            body.append("• ").append(assignment.label()).append(": ")
                    .append(range(assignment.start(), assignment.end()));
            if (assignment.estimatedEnd()) body.append(" (estimated end; continue until transport is finished)");
            body.append("\n");
        }
        return body.append("\nAssignments can change. View your latest schedule:\n")
                .append(baseUrl.replaceAll("/+$", "")).append("/my-driving-assignments?date=")
                .append(workStart.toLocalDate())
                .append("\n\nWhen you start driving, use the shuttle QR code or NFC tag to record your run.\n")
                .append("\nIs the Shuttle Running\n").toString();
    }

    private String range(LocalDateTime start, LocalDateTime end) {
        return start.format(TIME) + "–" + end.format(TIME)
                + (end.toLocalDate().equals(start.toLocalDate()) ? "" : " (" + end.toLocalDate().format(DATE) + ")");
    }
}

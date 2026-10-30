package technology.renatus.routepulse.pickup;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record PickupReminder(Long deliveryId,Long organizationId,String recipient,String driverName,PickupAlerts.Alert alert) {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a",Locale.US);
    public String subject() { return "Pickup assistance — scheduled off at " + alert.row().clockOut().format(DateTimeFormatter.ofPattern("h:mm a",Locale.US)); }
    public String body(String baseUrl) {
        return "Hello "+driverName+",\n\n"+summary(alert)+"\n\n"
                +"This is a scheduled clock-out, not confirmation that the rider is waiting. Pickup assistance is optional; help if you are available. No response or pickup confirmation is required.\n\n"
                +"View pickup details:\n"
                +baseUrl.replaceAll("/+$","")+"/my-pickup-assistance?date="+alert.row().clockOut().toLocalDate()
                +"\n\nThis is an automated notification. Please do not reply. Contact your supervisor with questions.\n";
    }
    public static String summary(PickupAlerts.Alert alert) {
        return alert.row().name()+" — Scheduled off "+alert.row().clockOut().format(TIME)+" ("+alert.row().clockOut().getZone()+")\n"
                +"Location: "+alert.row().location()
                +(alert.row().stale()?"\nSchedule may be out of date.":"");
    }
}

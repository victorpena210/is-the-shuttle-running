package technology.renatus.routepulse.scheduling.demand;

/** The work required during a planned shuttle interval. */
public enum ShuttleService {
    MORNING_OPENING("Morning · opening service"),
    MIDDAY_OPENING("Midday · opening service"),
    SCARE_ACTORS("Fright Fest · required scare-actor transport"),
    EMPLOYEE_PICKUP("Employee pickups");

    private final String label;
    ShuttleService(String label) { this.label = label; }
    public String getLabel() { return label; }
}

package technology.renatus.routepulse.operations.domain;

public enum RiderOperationalStatus {
    RUNNING("Running"),
    WAITING_AT_PICKUP("Waiting at pickup"),
    CONFIRMATION_NEEDED("Status unconfirmed"),
    TEMPORARILY_PAUSED("Temporarily paused"),
    DELAYED("Delayed"),
    NO_SERVICE_SCHEDULED("No service scheduled"),
    SERVICE_ENDING_SOON("Service ending soon"),
    NOT_RUNNING("Not running");

    private final String label;

    RiderOperationalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

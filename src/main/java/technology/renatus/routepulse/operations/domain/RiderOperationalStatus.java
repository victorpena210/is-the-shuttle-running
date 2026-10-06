package technology.renatus.routepulse.operations.domain;

public enum RiderOperationalStatus {
    RUNNING("Running"),
    CONFIRMATION_NEEDED("Confirmation needed"),
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

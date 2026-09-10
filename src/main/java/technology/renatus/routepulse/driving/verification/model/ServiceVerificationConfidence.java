package technology.renatus.routepulse.driving.verification.model;

import java.util.Locale;

/** Confidence that independent RoutePulse signals corroborate shuttle activity. */
public enum ServiceVerificationConfidence {
    HIGH("Verified"),
    MEDIUM("Supported"),
    LOW("Needs review"),
    INSUFFICIENT("Limited data"),
    UPCOMING("Upcoming");

    private final String label;

    ServiceVerificationConfidence(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCssModifier() {
        return name().toLowerCase(Locale.ROOT);
    }
}
